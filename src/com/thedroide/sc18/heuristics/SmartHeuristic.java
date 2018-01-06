package com.thedroide.sc18.heuristics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.antelmann.game.GameRuntimeException;
import com.thedroide.sc18.core.HUIGameState;
import com.thedroide.sc18.core.HUIMove;
import com.thedroide.sc18.core.HUIPlayerColor;

import sc.plugin2018.Action;
import sc.plugin2018.ExchangeCarrots;
import sc.plugin2018.FallBack;
import sc.plugin2018.Player;

/**
 * Provides a more-or-less good implementation
 * of a {@link HUIHeuristic} that is based
 * on player statistics.
 */
public class SmartHeuristic implements HUIHeuristic {
	private static final Logger LOG = LoggerFactory.getLogger("ownlog");
	private static final double GOOD_HEURISTIC = Double.POSITIVE_INFINITY;
	private static final double BAD_HEURISTIC = Double.NEGATIVE_INFINITY;
	
	private final int carrotWeight = 1;
	private final int saladWeight = 256;
	private final int fieldIndexWeight = 4;
	
	@Override
	public double heuristic(
			HUIGameState gameBeforeMove,
			HUIGameState gameAfterMove,
			HUIMove move,
			HUIPlayerColor player
	) {
		if (move.isDiscarded()) {
			return BAD_HEURISTIC;
		}
		
		try {
			Player playerBeforeMove = player.getSCPlayer(gameBeforeMove);
			Player playerAfterMove = player.getSCPlayer(gameAfterMove);
			Action lastAction = playerBeforeMove.getLastNonSkipAction();
			
			if (playerAfterMove.inGoal()) {
				// Obviously a very good rating if the player reaches the goal:
				return GOOD_HEURISTIC;
			} else if (lastAction instanceof ExchangeCarrots || lastAction instanceof FallBack) {
				return BAD_HEURISTIC;
			}
			
			return rate(playerAfterMove);
		} catch (GameRuntimeException e) {
			LOG.warn("Exception while calculating heuristic: ", e);
			return BAD_HEURISTIC;
		}
	}

	private double rate(Player player) {
		int salads = player.getSalads();
		int carrots = player.getCarrots();
		int fieldIndex = player.getFieldIndex(); // Maximum field index is 64
		
		int saladRating = -(salads * saladWeight); // Less salads: better
		int fieldRating = fieldIndex * fieldIndexWeight; // Higher field: better
		int carrotRating = -Math.abs((carrots - carrotOptimum(fieldIndex)) * carrotWeight) / 4; // More or less carrots than optimum: worse
		
		return saladRating + fieldRating + carrotRating;
	}
	
	private int carrotOptimum(int fieldIndex) {
		int fieldsToGoal = 64 - fieldIndex;
		
		/*
		 * A linear function is used to determine the carrot optimum,
		 * because we want to have a bunch of carrots in the beginning,
		 * but as we approach the end of the track, we need to drop at
		 * least below 10 carrots or we won't be able to reach the goal.
		 */
		
		return (fieldsToGoal + 6) / 2;
	}

	@Override
	public boolean pruneMove(
			HUIGameState gameBeforeMove,
			HUIGameState gameAfterMove,
			HUIMove move,
			HUIPlayerColor player
	) {
		try {
			if (gameAfterMove.getWinner() != null
					|| (move.isCarrotExchange() && player.getSCPlayer(gameBeforeMove).getLastNonSkipAction() instanceof ExchangeCarrots)) {
				return true;
			}
			
			return false;
		} catch (Exception e) {
			LOG.warn("Exception while pruning move: ", e);
			return false;
		}
	}
}
