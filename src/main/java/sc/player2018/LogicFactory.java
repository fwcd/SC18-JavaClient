package sc.player2018;

import com.thedroide.sc18.core.PhantomLogic;

import sc.plugin2018.AbstractClient;
import sc.plugin2018.IGameHandler;

/**
 * Stores the available game logics and the associated
 * constructors.
 */
public enum LogicFactory {
	SIMPLE(SimpleLogic::new), // The SimpleClient-logic
	MY_LOGIC(PhantomLogic::new); // My game logic adapter
	
	private final LogicBuilder builder;
	
	private LogicFactory(LogicBuilder builder) {
		this.builder = builder;
	}
	
	/**
	 * Fetches the logic used.
	 */
	public static LogicFactory getDefault() {
		return MY_LOGIC;
	}

	/**
	 * Creates and returns a new logic instance.
	 */
	public IGameHandler createInstance(AbstractClient client) {
		return builder.build(client);
	}
}
