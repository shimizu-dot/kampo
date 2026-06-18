package com.example.kanpo.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.example.kanpo.view.KampoBackupFileView;
import com.example.kanpo.view.KampoBackupResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Service
public class KampoBackupService {

	private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final JdbcTemplate jdbcTemplate;
	private final DataSource dataSource;

	public KampoBackupService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
		this.jdbcTemplate = jdbcTemplate;
		this.dataSource = dataSource;
	}

	public KampoBackupResult createBackup() throws IOException {
		Path backupDir = getBackupDir();
		Files.createDirectories(backupDir);

		String timestamp = LocalDateTime.now().format(FILE_NAME_FORMAT);
		String fileName = "kampo_backup_" + timestamp + ".sql";
		Path filePath = backupDir.resolve(fileName);

		String backupSql = buildBackupSql();
		Files.writeString(filePath, backupSql, StandardCharsets.UTF_8);

		KampoBackupResult result = new KampoBackupResult();
		result.setFileName(fileName);
		result.setFilePath(filePath.toAbsolutePath().toString());
		result.setFileSizeBytes(Files.size(filePath));
		result.setCreatedAt(LocalDateTime.now().toString());
		result.setProductCount(count("SELECT COUNT(*) FROM kampo_products"));
		result.setIngredientCount(count("SELECT COUNT(*) FROM kampo_ingredients"));
		result.setProductIngredientCount(count("SELECT COUNT(*) FROM kampo_product_ingredients"));
		return result;
	}

	public List<KampoBackupFileView> listBackupFiles() throws IOException {
		Path backupDir = getBackupDir();
		if (!Files.exists(backupDir)) {
			return List.of();
		}
		try (var stream = Files.list(backupDir)) {
			return stream
				.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".sql"))
				.sorted(Comparator.comparingLong((Path path) -> {
					try {
						return Files.getLastModifiedTime(path).toMillis();
					} catch (IOException e) {
						throw new IllegalStateException("バックアップ一覧の並び替えに失敗しました。", e);
					}
				}).reversed())
				.map(this::toBackupFileView)
				.toList();
		}
	}

	public Resource getBackupResource(String fileName) throws IOException {
		Path backupFile = resolveBackupFile(fileName);
		return new FileSystemResource(backupFile);
	}

	public KampoBackupResult restoreBackup(String fileName) throws IOException {
		Path backupFile = resolveBackupFile(fileName);
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(backupFile));
		populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
		populator.setContinueOnError(false);
		populator.execute(dataSource);

		KampoBackupResult result = new KampoBackupResult();
		result.setFileName(backupFile.getFileName().toString());
		result.setFilePath(backupFile.toAbsolutePath().toString());
		result.setFileSizeBytes(Files.size(backupFile));
		result.setCreatedAt(LocalDateTime.now().format(TIMESTAMP_FORMAT));
		result.setProductCount(count("SELECT COUNT(*) FROM kampo_products"));
		result.setIngredientCount(count("SELECT COUNT(*) FROM kampo_ingredients"));
		result.setProductIngredientCount(count("SELECT COUNT(*) FROM kampo_product_ingredients"));
		return result;
	}

	private String buildBackupSql() throws IOException {
		StringBuilder builder = new StringBuilder();
		builder.append("-- Kampo database backup\n");
		builder.append("-- Generated at ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append('\n');
		builder.append('\n');
		builder.append(loadSchema()).append('\n');
		builder.append('\n');
		builder.append("BEGIN;\n\n");
		builder.append("TRUNCATE TABLE kampo_product_ingredients, kampo_ingredients, kampo_products RESTART IDENTITY CASCADE;\n\n");
		appendProducts(builder);
		builder.append('\n');
		appendIngredients(builder);
		builder.append('\n');
		appendProductIngredients(builder);
		builder.append('\n');
		builder.append("SELECT setval(pg_get_serial_sequence('kampo_products','id'), COALESCE((SELECT MAX(id) FROM kampo_products), 1), EXISTS (SELECT 1 FROM kampo_products));\n");
		builder.append("SELECT setval(pg_get_serial_sequence('kampo_ingredients','id'), COALESCE((SELECT MAX(id) FROM kampo_ingredients), 1), EXISTS (SELECT 1 FROM kampo_ingredients));\n");
		builder.append("SELECT setval(pg_get_serial_sequence('kampo_product_ingredients','id'), COALESCE((SELECT MAX(id) FROM kampo_product_ingredients), 1), EXISTS (SELECT 1 FROM kampo_product_ingredients));\n");
		builder.append("\nCOMMIT;\n");
		return builder.toString();
	}

	private String loadSchema() throws IOException {
		ClassPathResource resource = new ClassPathResource("schema.sql");
		try (InputStream inputStream = resource.getInputStream()) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private void appendProducts(StringBuilder builder) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
			SELECT id, identification_code, sales_name, reading, efficacy_condition_text, efficacy_indication_text,
			       dosage_daily_amount, dosage_instructions_text, source_file_name, source_document_no,
			       created_at, updated_at
			FROM kampo_products
			ORDER BY id
			""");
		if (rows.isEmpty()) {
			builder.append("-- kampo_products: no rows\n");
			return;
		}
		appendInsertHeader(builder, "kampo_products",
			"id, identification_code, sales_name, reading, efficacy_condition_text, efficacy_indication_text, dosage_daily_amount, dosage_instructions_text, source_file_name, source_document_no, created_at, updated_at");
		appendValues(builder, rows, row -> new Object[] {
			row.get("id"),
			row.get("identification_code"),
			row.get("sales_name"),
			row.get("reading"),
			row.get("efficacy_condition_text"),
			row.get("efficacy_indication_text"),
			row.get("dosage_daily_amount"),
			row.get("dosage_instructions_text"),
			row.get("source_file_name"),
			row.get("source_document_no"),
			row.get("created_at"),
			row.get("updated_at")
		});
	}

	private void appendIngredients(StringBuilder builder) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
			SELECT id, ingredient_name, created_at, updated_at
			FROM kampo_ingredients
			ORDER BY id
			""");
		if (rows.isEmpty()) {
			builder.append("-- kampo_ingredients: no rows\n");
			return;
		}
		appendInsertHeader(builder, "kampo_ingredients", "id, ingredient_name, created_at, updated_at");
		appendValues(builder, rows, row -> new Object[] {
			row.get("id"),
			row.get("ingredient_name"),
			row.get("created_at"),
			row.get("updated_at")
		});
	}

	private void appendProductIngredients(StringBuilder builder) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
			SELECT id, product_id, ingredient_id, amount_value, amount_unit, sort_order, raw_amount_text, created_at, updated_at
			FROM kampo_product_ingredients
			ORDER BY id
			""");
		if (rows.isEmpty()) {
			builder.append("-- kampo_product_ingredients: no rows\n");
			return;
		}
		appendInsertHeader(builder, "kampo_product_ingredients",
			"id, product_id, ingredient_id, amount_value, amount_unit, sort_order, raw_amount_text, created_at, updated_at");
		appendValues(builder, rows, row -> new Object[] {
			row.get("id"),
			row.get("product_id"),
			row.get("ingredient_id"),
			row.get("amount_value"),
			row.get("amount_unit"),
			row.get("sort_order"),
			row.get("raw_amount_text"),
			row.get("created_at"),
			row.get("updated_at")
		});
	}

	private void appendInsertHeader(StringBuilder builder, String tableName, String columns) {
		builder.append("-- ").append(tableName).append('\n');
		builder.append("INSERT INTO ").append(tableName).append(" (").append(columns).append(") VALUES\n");
	}

	private void appendValues(StringBuilder builder, List<Map<String, Object>> rows, RowExtractor extractor) {
		if (rows.isEmpty()) {
			builder.append("-- no rows\n");
			return;
		}
		for (int index = 0; index < rows.size(); index++) {
			Object[] values = extractor.extract(rows.get(index));
			builder.append("  (");
			for (int i = 0; i < values.length; i++) {
				builder.append(sqlLiteral(values[i]));
				if (i < values.length - 1) {
					builder.append(", ");
				}
			}
			builder.append(")");
			if (index < rows.size() - 1) {
				builder.append(",\n");
			} else {
				builder.append(";\n");
			}
		}
	}

	private String sqlLiteral(Object value) {
		if (value == null) {
			return "NULL";
		}
		if (value instanceof Number number) {
			return number.toString();
		}
		if (value instanceof Boolean bool) {
			return bool ? "TRUE" : "FALSE";
		}
		if (value instanceof Timestamp timestamp) {
			return "'" + TIMESTAMP_FORMAT.format(timestamp.toLocalDateTime()) + "'";
		}
		if (value instanceof LocalDateTime localDateTime) {
			return "'" + TIMESTAMP_FORMAT.format(localDateTime) + "'";
		}
		if (value instanceof BigDecimal bigDecimal) {
			return bigDecimal.toPlainString();
		}
		String text = value.toString().replace("'", "''");
		return "'" + text + "'";
	}

	private long count(String sql) {
		Long result = jdbcTemplate.queryForObject(sql, Long.class);
		return result == null ? 0L : result;
	}

	private Path getBackupDir() {
		return Paths.get("src", "backup");
	}

	private Path resolveBackupFile(String fileName) throws IOException {
		if (!StringUtils.hasText(fileName)) {
			throw new IOException("バックアップファイルが指定されていません。");
		}
		String normalized = fileName.trim();
		if (normalized.contains("/") || normalized.contains("\\") || !normalized.endsWith(".sql")) {
			throw new IOException("不正なバックアップファイル名です。");
		}
		Path backupDir = getBackupDir().toAbsolutePath().normalize();
		Path backupFile = backupDir.resolve(normalized).normalize();
		if (!backupFile.startsWith(backupDir)) {
			throw new IOException("不正なバックアップファイル名です。");
		}
		if (!Files.exists(backupFile)) {
			throw new IOException("指定されたバックアップファイルが見つかりません。");
		}
		return backupFile;
	}

	private KampoBackupFileView toBackupFileView(Path path) {
		try {
			KampoBackupFileView view = new KampoBackupFileView();
			view.setFileName(path.getFileName().toString());
			view.setFilePath(path.toAbsolutePath().toString());
			view.setFileSizeBytes(Files.size(path));
			view.setLastModifiedAt(Files.getLastModifiedTime(path)
				.toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime()
				.format(TIMESTAMP_FORMAT));
			return view;
		} catch (IOException e) {
			throw new IllegalStateException("バックアップ一覧の読み込みに失敗しました。", e);
		}
	}

	@FunctionalInterface
	private interface RowExtractor {
		Object[] extract(Map<String, Object> row);
	}
}
