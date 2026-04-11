package com.mysite.sbb.mypage.service;

import java.util.ArrayList; 

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysite.sbb.mypage.entity.EquipmentSlot;
import com.mysite.sbb.mypage.entity.Item;
import com.mysite.sbb.mypage.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class RecommendationService {

	private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
	private static final double STEP_UPPER_RATIO = 1.30d;
	private static final double BALANCED_TOLERANCE_RATIO = 0.15d;

	private final ItemRepository itemRepository;
	private final ObjectMapper objectMapper;

	public StatSummary calculateTotals(Collection<Item> items) {
		StatAccumulator accumulator = new StatAccumulator();
		for (Item item : items) {
			accumulator.add(profileForItem(item));
		}
		return accumulator.toSummary();
	}

	public List<RecommendationResult> recommend(Map<String, Item> equippedBySlot, int limit) {
	    List<RecommendationResult> recommendations = new ArrayList<>();

	    double equippedAveragePower = equippedBySlot.values().stream()
	            .filter(Objects::nonNull)
	            .map(this::profileForItem)
	            .mapToDouble(StatProfile::powerScore)
	            .average()
	            .orElse(0d);

	    for (EquipmentSlot slot : EquipmentSlot.orderedValues()) {
	        Item currentItem = equippedBySlot.get(slot.getCode());
	        double currentPowerScore = currentItem == null ? 0d : profileForItem(currentItem).powerScore();

	        Optional<Item> candidate = this.itemRepository
	                .findBySlotCodeOrderByPowerScoreDescNameAsc(slot.getCode())
	                .stream()
	                .filter(item -> currentItem == null || !Objects.equals(item.getId(), currentItem.getId()))
	                .filter(item -> item.getPowerScore() != null)
	                .filter(item -> currentItem == null || defaultDouble(item.getPowerScore()) > currentPowerScore + 0.01d)
	                .filter(item -> currentItem == null || defaultDouble(item.getPowerScore()) <= currentPowerScore * STEP_UPPER_RATIO)
	                .min(Comparator.comparingDouble(item ->
	                        currentItem == null
	                                ? defaultDouble(item.getPowerScore())
	                                : defaultDouble(item.getPowerScore()) - currentPowerScore));

	        if (candidate.isEmpty()) {
	            continue;
	        }

	        StatProfile currentProfile = profileForItem(currentItem);
	        StatProfile candidateProfile = profileForItem(candidate.get());
	        StatDelta delta = delta(candidateProfile, currentProfile);

	        recommendations.add(new RecommendationResult(
	                slot,
	                currentItem,
	                candidate.get(),
	                delta,
	                buildReason(slot, currentItem, delta)));
	    }

	    recommendations.sort((a, b) -> compareRecommendation(a, b, equippedAveragePower));

	    return recommendations.stream()
	            .limit(limit)
	            .toList();
	}
	private int compareRecommendation(RecommendationResult a, RecommendationResult b, double equippedAveragePower) {
	    int aStage = upgradeStagePriority(a, equippedAveragePower);
	    int bStage = upgradeStagePriority(b, equippedAveragePower);
	    if (aStage != bStage) {
	        return Integer.compare(aStage, bStage);
	    }

	    if (aStage == 1) {
	        double aDeficit = deficitFromAverage(a, equippedAveragePower);
	        double bDeficit = deficitFromAverage(b, equippedAveragePower);
	        int deficitCompare = Double.compare(bDeficit, aDeficit);
	        if (deficitCompare != 0) {
	            return deficitCompare;
	        }
	    }

	    int categoryCompare = Integer.compare(categoryPriority(a.slot()), categoryPriority(b.slot()));
	    if (categoryCompare != 0) {
	        return categoryCompare;
	    }

	    return Double.compare(b.delta().powerScoreDelta(), a.delta().powerScoreDelta());
	}

	private int upgradeStagePriority(RecommendationResult result, double equippedAveragePower) {
	    double currentPower = currentPower(result);

	    if (currentPower <= 0d) {
	        return 0;
	    }

	    if (isBelowAverageBand(currentPower, equippedAveragePower)) {
	        return 1;
	    }

	    return 2;
	}

	private boolean isBelowAverageBand(double currentPower, double equippedAveragePower) {
	    if (equippedAveragePower <= 0d) {
	        return false;
	    }
	    return currentPower < equippedAveragePower * (1d - BALANCED_TOLERANCE_RATIO);
	}

	private double deficitFromAverage(RecommendationResult result, double equippedAveragePower) {
	    double currentPower = currentPower(result);
	    return Math.max(0d, equippedAveragePower - currentPower);
	}

	private double currentPower(RecommendationResult result) {
	    if (result.currentItem() == null) {
	        return 0d;
	    }
	    return profileForItem(result.currentItem()).powerScore();
	}

	private int categoryPriority(EquipmentSlot slot) {
	    String code = slot.getCode().toLowerCase(Locale.ROOT);

	    if (isWeaponSlot(code)) {
	        return 0;
	    }
	    if (isGaderSlot(code)) {
	        return 1;
	    }
	    if (isAccessorySlot(code)) {
	        return 2;
	    }
	    if (isArmorSlot(code)) {
	        return 3;
	    }
	    return 4;
	}

	private boolean isWeaponSlot(String code) {
	    return code.contains("weapon") || code.contains("wpn");
	}

	private boolean isGaderSlot(String code) {
	    return code.contains("gader") || code.contains("guard");
	}

	private boolean isAccessorySlot(String code) {
	    return code.contains("ring")
	            || code.contains("earring")
	            || code.contains("necklace")
	            || code.contains("bracelet")
	            || code.contains("belt")
	            || code.contains("acc");
	}

	private boolean isArmorSlot(String code) {
	    return code.contains("helmet")
	            || code.contains("head")
	            || code.contains("chest")
	            || code.contains("top")
	            || code.contains("bottom")
	            || code.contains("pants")
	            || code.contains("glove")
	            || code.contains("boots")
	            || code.contains("shoe")
	            || code.contains("shoulder")
	            || code.contains("armor");
	}

	public StatDelta compare(Item candidateItem, Item currentItem) {
		return delta(profileForItem(candidateItem), profileForItem(currentItem));
	}

	public StatProfile profileForItem(Item item) {
		if (item == null) {
			return StatProfile.empty();
		}

		StatAccumulator accumulator = new StatAccumulator();
		accumulateNode(item.getMainStatsJson(), accumulator, true);
		accumulateNode(item.getSubStatsJson(), accumulator, false);
		accumulateNode(item.getMagicStoneStatJson(), accumulator, false);
		accumulateNode(item.getGodStoneStatJson(), accumulator, false);

		double gradeBonus = gradeBonus(item.getGrade());
		if (gradeBonus > 0d) {
			accumulator.addSummary("등급 보정", gradeBonus);
		}

		if (defaultBoolean(item.getEnchantable())) {
			accumulator.addSummary("강화 잠재력", defaultInteger(item.getMaxEnchantLevel()) * 1.5d);
		}

		accumulator.powerScore += powerScore(accumulator, item);
		return accumulator.toProfile();
	}

	private void accumulateNode(String json, StatAccumulator accumulator, boolean allowAttackRange) {
		if (json == null || json.isBlank()) {
			return;
		}

		try {
			JsonNode root = this.objectMapper.readTree(json);
			if (root == null || !root.isArray()) {
				return;
			}

			for (JsonNode node : root) {
				String name = text(node, "name");
				if (name == null || name.isBlank()) {
					continue;
				}

				double value = numericValue(text(node, "value"));
				double minValue = numericValue(text(node, "minValue"));
				accumulator.addSummary(name, value);

				String normalized = normalize(name);
				if (normalized.contains("공격력")) {
					double attackMin = minValue > 0d ? minValue : value;
					double attackMax = value;
					if (allowAttackRange) {
						accumulator.attackMin += attackMin;
						accumulator.attackMax += attackMax;
					} else {
						accumulator.attackMin += attackMin;
						accumulator.attackMax += attackMax;
					}
					continue;
				}

				if (normalized.contains("방어력")) {
					accumulator.defense += value;
					continue;
				}
				if (normalized.contains("치명타")) {
					accumulator.critical += value;
					continue;
				}
				if (normalized.contains("생명력")) {
					accumulator.health += value;
					continue;
				}
				if (normalized.contains("마법증폭")) {
					accumulator.magicBoost += value;
					continue;
				}
				if (normalized.contains("마법적중")) {
					accumulator.magicAccuracy += value;
					continue;
				}
				if (normalized.contains("pve피해증폭")) {
					accumulator.pveAttack += value;
					continue;
				}
				if (normalized.contains("치유량")) {
					accumulator.healingBoost += value;
					continue;
				}
				if (normalized.contains("명중")) {
					accumulator.accuracy += value;
				}
			}
		} catch (Exception ignored) {
			// Ignore malformed payloads and keep the sync resilient.
		}
	}

	private StatDelta delta(StatProfile candidate, StatProfile current) {
		Map<String, Double> summaryDelta = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : candidate.summary().entrySet()) {
			double deltaValue = entry.getValue() - current.summary().getOrDefault(entry.getKey(), 0d);
			if (Math.abs(deltaValue) > 0.001d) {
				summaryDelta.put(entry.getKey(), round(deltaValue));
			}
		}
		for (Map.Entry<String, Double> entry : current.summary().entrySet()) {
			if (candidate.summary().containsKey(entry.getKey())) {
				continue;
			}
			double deltaValue = -entry.getValue();
			if (Math.abs(deltaValue) > 0.001d) {
				summaryDelta.put(entry.getKey(), round(deltaValue));
			}
		}

		return new StatDelta(
				round(candidate.attackMin() - current.attackMin()),
				round(candidate.attackMax() - current.attackMax()),
				round(candidate.defense() - current.defense()),
				round(candidate.accuracy() - current.accuracy()),
				round(candidate.critical() - current.critical()),
				round(candidate.health() - current.health()),
				round(candidate.magicBoost() - current.magicBoost()),
				round(candidate.magicAccuracy() - current.magicAccuracy()),
				round(candidate.pveAttack() - current.pveAttack()),
				round(candidate.healingBoost() - current.healingBoost()),
				round(candidate.powerScore() - current.powerScore()),
				summaryDelta);
	}

	private String buildReason(EquipmentSlot slot, Item currentItem, StatDelta delta) {
		List<String> highlights = delta.summaryDelta().entrySet().stream()
				.filter(entry -> entry.getValue() > 0d)
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(3)
				.map(entry -> entry.getKey() + " +" + formatNumber(entry.getValue()))
				.toList();

		if (currentItem == null) {
			if (highlights.isEmpty()) {
				return slot.getLabel() + " 슬롯이 비어 있어 기본 전투력이 크게 오릅니다.";
			}
			return slot.getLabel() + " 슬롯이 비어 있어 " + String.join(", ", highlights) + " 상승을 바로 챙길 수 있습니다.";
		}
		if (highlights.isEmpty()) {
			return "현재 장비보다 전반적인 전투력 효율이 높습니다.";
		}
		return String.join(", ", highlights) + " 기준으로 가장 큰 개선폭이 예상됩니다.";
	}

	private double powerScore(StatAccumulator accumulator, Item item) {
		double score = 0d;
		score += accumulator.attackMax * 4.0d;
		score += accumulator.defense * 1.7d;
		score += accumulator.accuracy * 1.4d;
		score += accumulator.critical * 1.5d;
		score += accumulator.health * 0.18d;
		score += accumulator.magicBoost * 2.6d;
		score += accumulator.magicAccuracy * 1.5d;
		score += accumulator.pveAttack * 28d;
		score += accumulator.healingBoost * 18d;
		score += gradeBonus(item.getGrade());
		score += defaultInteger(item.getEnchantLevel()) * 2.5d;
		score += defaultInteger(item.getMagicStoneSlotCount()) * 4d;
		score += defaultInteger(item.getGodStoneSlotCount()) * 8d;
		return round(score);
	}

	private double gradeBonus(String grade) {
		if (grade == null) {
			return 0d;
		}
		return switch (grade.toLowerCase(Locale.ROOT)) {
		case "common" -> 10d;
		case "rare" -> 25d;
		case "legend" -> 45d;
		case "unique" -> 65d;
		case "epic" -> 90d;
		default -> 0d;
		};
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		return value == null || value.isNull() ? null : value.asText();
	}

	private double numericValue(String value) {
		if (value == null || value.isBlank()) {
			return 0d;
		}
		Matcher matcher = NUMBER_PATTERN.matcher(value.replace(",", ""));
		if (!matcher.find()) {
			return 0d;
		}
		return Double.parseDouble(matcher.group());
	}

	private String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.replace(" ", "").toLowerCase(Locale.ROOT);
	}

	private double round(double value) {
		return Math.round(value * 100d) / 100d;
	}

	private String formatNumber(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.0001d) {
			return String.valueOf((long) Math.rint(value));
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private boolean defaultBoolean(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private int defaultInteger(Integer value) {
		return value == null ? 0 : value;
	}

	private double defaultDouble(Double value) {
		return value == null ? 0d : value;
	}

	public record StatSummary(
			double attackMin,
			double attackMax,
			double defense,
			double accuracy,
			double critical,
			double health,
			double magicBoost,
			double magicAccuracy,
			double pveAttack,
			double healingBoost,
			double powerScore,
			Map<String, Double> summary) {

		public static StatSummary empty() {
			return new StatSummary(0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, Map.of());
		}
	}

	public record StatProfile(
			double attackMin,
			double attackMax,
			double defense,
			double accuracy,
			double critical,
			double health,
			double magicBoost,
			double magicAccuracy,
			double pveAttack,
			double healingBoost,
			double powerScore,
			Map<String, Double> summary) {

		public static StatProfile empty() {
			return new StatProfile(0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, Map.of());
		}
	}

	public record StatDelta(
			double attackMinDelta,
			double attackMaxDelta,
			double defenseDelta,
			double accuracyDelta,
			double criticalDelta,
			double healthDelta,
			double magicBoostDelta,
			double magicAccuracyDelta,
			double pveAttackDelta,
			double healingBoostDelta,
			double powerScoreDelta,
			Map<String, Double> summaryDelta) {
	}

	public record RecommendationResult(
			EquipmentSlot slot,
			Item currentItem,
			Item candidateItem,
			StatDelta delta,
			String reason) {
	}

	private static final class StatAccumulator {

		private final Map<String, Double> summary = new LinkedHashMap<>();
		private double attackMin;
		private double attackMax;
		private double defense;
		private double accuracy;
		private double critical;
		private double health;
		private double magicBoost;
		private double magicAccuracy;
		private double pveAttack;
		private double healingBoost;
		private double powerScore;

		private void add(StatProfile profile) {
			this.attackMin += profile.attackMin();
			this.attackMax += profile.attackMax();
			this.defense += profile.defense();
			this.accuracy += profile.accuracy();
			this.critical += profile.critical();
			this.health += profile.health();
			this.magicBoost += profile.magicBoost();
			this.magicAccuracy += profile.magicAccuracy();
			this.pveAttack += profile.pveAttack();
			this.healingBoost += profile.healingBoost();
			this.powerScore += profile.powerScore();
			profile.summary().forEach(this::addSummary);
		}

		private void addSummary(String name, double value) {
			if (Math.abs(value) < 0.0001d) {
				return;
			}
			this.summary.merge(name, value, Double::sum);
		}

		private StatSummary toSummary() {
			return new StatSummary(
					attackMin,
					attackMax,
					defense,
					accuracy,
					critical,
					health,
					magicBoost,
					magicAccuracy,
					pveAttack,
					healingBoost,
					powerScore,
					summary.entrySet().stream().collect(LinkedHashMap::new,
							(map, entry) -> map.put(entry.getKey(), Math.round(entry.getValue() * 100d) / 100d),
							Map::putAll));
		}

		private StatProfile toProfile() {
			return new StatProfile(
					attackMin,
					attackMax,
					defense,
					accuracy,
					critical,
					health,
					magicBoost,
					magicAccuracy,
					pveAttack,
					healingBoost,
					powerScore,
					summary.entrySet().stream().collect(LinkedHashMap::new,
							(map, entry) -> map.put(entry.getKey(), Math.round(entry.getValue() * 100d) / 100d),
							Map::putAll));
		}
	}
}
