package com.example.kanpo.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

import com.example.kanpo.importer.KampoIngredientDraft;
import com.example.kanpo.importer.KampoImportDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class KampoImportRepository {

	private final JdbcTemplate jdbcTemplate;

	public KampoImportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public long insertProduct(KampoImportDraft draft) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO kampo_products (
					identification_code,
					sales_name,
					efficacy_condition_text,
					efficacy_indication_text,
					dosage_daily_amount,
					dosage_instructions_text,
					source_file_name,
					source_document_no
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, draft.getIdentificationCode());
			ps.setString(2, draft.getSalesName());
			ps.setString(3, draft.getEfficacyConditionText());
			ps.setString(4, draft.getEfficacyIndicationText());
			ps.setBigDecimal(5, draft.getDosageDailyAmount());
			ps.setString(6, draft.getDosageInstructionsText());
			ps.setString(7, draft.getSourceFileName());
			ps.setString(8, draft.getSourceDocumentNo());
			return ps;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("商品データの登録に失敗しました。");
		}
		return key.longValue();
	}

	public long findOrCreateIngredient(String ingredientName) {
		List<Long> ids = jdbcTemplate.query(
				"SELECT id FROM kampo_ingredients WHERE ingredient_name = ?",
				(rs, rowNum) -> rs.getLong("id"),
				ingredientName);
		if (!ids.isEmpty()) {
			return ids.get(0);
		}

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO kampo_ingredients (
					ingredient_name
				) VALUES (?)
				""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, ingredientName);
			return ps;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("有効成分の登録に失敗しました: " + ingredientName);
		}
		return key.longValue();
	}

	public void insertProductIngredient(long productId, long ingredientId, KampoIngredientDraft draft) {
		jdbcTemplate.update("""
			INSERT INTO kampo_product_ingredients (
				product_id,
				ingredient_id,
				amount_value,
				amount_unit,
				sort_order,
				raw_amount_text
			) VALUES (?, ?, ?, ?, ?, ?)
			""",
			productId,
			ingredientId,
			draft.getAmountValue(),
			Objects.requireNonNullElse(draft.getAmountUnit(), "g"),
			draft.getSortOrder(),
			draft.getRawAmountText());
	}
}
