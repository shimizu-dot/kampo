package com.example.kanpo.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

class KampoPdfParserTests {

	private final KampoPdfParser parser = new KampoPdfParser();

	@Test
	void parsesSamplePdf() throws Exception {
		ClassPathResource resource = new ClassPathResource("sample-pdfs/460026_5200001D1066_tsumura-anchusan.pdf");
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"460026_5200001D1066_1_10_ツムラ安中散エキス顆粒（医療用）.pdf",
				"application/pdf",
				resource.getInputStream());

		KampoImportDraft draft = parser.parse(file);

		assertThat(draft.getIdentificationCode()).isEqualTo("5");
		assertThat(draft.getSalesName()).isEqualTo("ツムラ安中散エキス顆粒");
		assertThat(draft.getReading()).isEqualTo("アンチュウサン");
		assertThat(draft.getEfficacyConditionText()).isEqualTo("やせ型で腹部筋肉が弛緩する傾向にあり、胃痛または腹痛があって、ときに胸やけ、げっぷ、食欲不振、はきけなど");
		assertThat(draft.getEfficacyIndicationText()).isEqualTo("神経性胃炎、慢性胃炎、胃アトニー");
		assertThat(draft.getDosageDailyAmount()).isEqualByComparingTo("7.5");
		assertThat(draft.getDosageInstructionsText()).isEqualTo("2～3回に分割し、食前又は食間に経口投与");
		assertThat(draft.getIngredients()).hasSize(7);
		assertThat(draft.getIngredients().get(0).getIngredientName()).isEqualTo("ケイヒ");
		assertThat(draft.getIngredients().get(0).getAmountValue()).isEqualByComparingTo("4.0");
	}
}
