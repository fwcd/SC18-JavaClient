package fwcd.sc18.utils;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import sc.plugin2018.Action;
import sc.plugin2018.Advance;
import sc.plugin2018.CardType;
import sc.plugin2018.ExchangeCarrots;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;
import sc.plugin2018.Player;
import sc.plugin2018.util.Constants;
import sc.shared.InvalidGameStateException;
import sc.shared.InvalidMoveException;
import sc.shared.PlayerColor;

/**
 * A collection of static utility methods and constants.
 */
public final class HUIUtils {
	public static final int MAX_FIELD = 64;
	public static final int CARROT_THRESHOLD = 360;
	public static final int LAST_SALAD_FIELD = 57;
	
	private HUIUtils() {}
	
	public static float[] generateWeights(int... layerSizes) {
		int weightCount = 0;
		for (int i=1; i<layerSizes.length; i++) {
			weightCount += (layerSizes[i - 1] + 1) * layerSizes[i];
		}
		
		float[] newWeights = new float[weightCount];
		return initWeights(newWeights);
	}

	public static boolean hasCyclicCarrotExchange(Player beforeMove, Player afterMove) {
		Action first = beforeMove.getLastNonSkipAction();
		Action second = afterMove.getLastNonSkipAction();
		
		if (first instanceof ExchangeCarrots && second instanceof ExchangeCarrots) {
			int value1 = ((ExchangeCarrots) first).getValue();
			int value2 = ((ExchangeCarrots) second).getValue();

			// Checks whether a drop/take cycle has occurred
			return (value1 > 0 && value2 < 0) || (value1 < 0 && value2 > 0);
		} else {
			return false;
		}
	}

	public static float[] initWeights(float[] newWeights) {
		Random random = ThreadLocalRandom.current();
		for (int i=0; i<newWeights.length; i++) {
			// Gaussian weight initialization
			newWeights[i] = (float) random.nextGaussian();
		}
		
		return newWeights;
	}
	
	public static float invertNormalize(float x, float min, float max) {
		return normalize(max - x, min, max);
	}
	
	public static float normalize(float x, float min, float max) {
		return (x - min) / (max - min);
	}
	
	public static String toString(Move move) {
		StringBuilder s = new StringBuilder("[Move] ");
		
		for (Action action : move.actions) {
			Class<? extends Action> clazz = action.getClass();
			s.append('(').append(clazz.getSimpleName());
			
			if (clazz == Advance.class) {
				s.append(" -> ").append(((Advance) action).getDistance());
			}
			
			s.append(") ");
		}
		
		return s.toString();
	}
	
	public static GameState spawnChild(GameState state, Move move) throws InvalidMoveException, InvalidGameStateException {
		try {
			GameState result = state.clone();
			move.perform(result);
			return result;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static PlayerColor getWinnerOrNull(GameState state) {
		Player red = state.getPlayer(PlayerColor.RED);
		Player blue = state.getPlayer(PlayerColor.BLUE);
		
		if (state.getRound() >= Constants.ROUND_LIMIT) {
			return red.getFieldIndex() > blue.getFieldIndex() ? PlayerColor.RED : PlayerColor.BLUE;
		} else if (red.inGoal()) {
			return PlayerColor.RED;
		} else if (blue.inGoal()) {
			return PlayerColor.BLUE;
		} else {
			return null;
		}
	}
	
	public static int distToNextField(FieldType fieldType, int currentIndex, GameState state) {
		return state.getNextFieldByType(fieldType, currentIndex) - currentIndex;
	}
	
	public static int distToPrevField(FieldType fieldType, int currentIndex, GameState state) {
		return state.getPreviousFieldByType(fieldType, currentIndex) - currentIndex;
	}
	
	public static int distToNextSalad(Player me, GameState state) {
		int myIndex = me.getFieldIndex();
		int distToSaladField = distToNextField(FieldType.SALAD, myIndex, state);
		int distToHareField = distToNextField(FieldType.HARE, myIndex, state);
		
		if (distToHareField < distToSaladField && me.ownsCardOfType(CardType.EAT_SALAD)) {
			return distToHareField;
		} else {
			return distToSaladField;
		}
	}
	
	public static boolean isGameOver(GameState state) {
		return state.getRound() >= Constants.ROUND_LIMIT
				|| state.getPlayer(PlayerColor.RED).inGoal()
				|| state.getPlayer(PlayerColor.BLUE).inGoal();
	}

	public static Set<Class<? extends Action>> getActionTypes(Move move) {
		Set<Class<? extends Action>> actionTypes = new HashSet<>();
		
		for (Action action : move.actions) {
			actionTypes.add(action.getClass());
		}
		
		return actionTypes;
	}
}
