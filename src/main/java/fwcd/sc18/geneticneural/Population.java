package fwcd.sc18.geneticneural;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fwcd.sc18.exception.CorruptedDataException;
import fwcd.sc18.utils.FloatList;
import fwcd.sc18.utils.IndexedHashMap;
import fwcd.sc18.utils.IndexedMap;
import fwcd.sc18.utils.MatchResult;

/**
 * A population that uses genetic techniques to
 * find optimize a solution.
 */
public class Population {
	private static final Logger GENETIC_LOG = LoggerFactory.getLogger("geneticlog");
	
	private final IndexedMap<float[], Float> individuals;
	private final Supplier<float[]> spawner;
	private final GeneticStrategy strategy;
	private final int survivorsPerGeneration;
	private final boolean trainMode;
	private final int trainIndex;
	
	private float mutatorWeight = 1F;
	private float mutatorBias = 0;

	private Path savePath = null;
	private String counterFile = "Counter";
	private String statsFile = "Stats";
	private String individualFilePrefix = "Individual";
	private String backupFolder = "Backup";
	private int maxStatsBytes = 100000;
	
	private int counter = 0;
	private int streak = 0;
	private int generation = 0;
	
	private int wins = 0;
	private int goalWins = 0;
	private int losses = 0;
	private int minGoalMoves = Integer.MAX_VALUE;
	private int maxGoalMoves = Integer.MIN_VALUE;
	private int longestStreak = 0;
	private float maxFitness = Float.NEGATIVE_INFINITY;
	
	/**
	 * Constructs a new population with the given hyperparameters. This
	 * method will try to load an exisiting population from the given
	 * folder and otherwise create a new one.
	 * 
	 * @param size - The amount of individuals in the new population
	 * @param spawner - A supply of new individuals
	 * @param savePath - The folder to which individuals will be serialized
	 * @param strategy - The strategy to be used
	 * @param trainMode - Whether the population should be created/loaded in training mode
	 * @param trainIndex - The index of the "training player" (relevant for selection)
	 */
	public Population(
			int size,
			Supplier<float[]> spawner,
			Path savePath,
			GeneticStrategy strategy,
			boolean trainMode,
			int trainIndex
	) {
		this.trainMode = trainMode;
		this.savePath = savePath;
		this.trainIndex = trainIndex;
		this.strategy = strategy;
		this.spawner = spawner;
		survivorsPerGeneration = size / 2;
		individuals = new IndexedHashMap<>();
		
		if (!loadAll(size)) {
			individuals.clear();
			Float initialFitness = Float.NEGATIVE_INFINITY;
			
			for (int i=0; i<size; i++) {
				float[] individual = spawner.get();
				
				put(individual, initialFitness);
			}
		}
	}
	
	/**
	 * Samples an individual from this population depending on the trainMode.
	 */
	public float[] sample() {
		return trainMode ? selectTrainingGenes() : selectFittestGenes();
	}
	
	/**
	 * Selects the current individual for training.
	 */
	private float[] selectTrainingGenes() {
		int size = individuals.size();
		
		if (counter < 0 || counter >= size) {
			counter = 0;
		}
		
		return strategy.selectTrainingGenes(individuals, counter)[trainIndex];
	}
	
	/**
	 * "Greedily" selects the individual with the highest (saved) fitness value.
	 */
	private float[] selectFittestGenes() {
		float bestFitness = Float.NEGATIVE_INFINITY;
		float[] bestIndividual = null;
		
		for (float[] individual : individuals.keySet()) {
			float fitness = individuals.get(individual);
			
			if (fitness > bestFitness) {
				bestFitness = fitness;
				bestIndividual = individual;
			}
		}
		
		if (bestIndividual == null) {
			throw new NoSuchElementException("Couldn't sample from an empty population");
		} else {
			return bestIndividual;
		}
	}
	
	/**
	 * Adds/replaces the given individual and it's associated fitness.
	 */
	public void put(float[] individual, float fitness) {
		individuals.put(individual, fitness);
	}
	
	/**
	 * Adds/replaces the given individual and it's associated fitness
	 * at the given index.
	 */
	public void put(int index, float[] individual, float fitness) {
		individuals.put(index, individual, fitness);
	}
	
	public float getFitness(float[] individual) {
		return individuals.get(individual);
	}
	
	/**
	 * Evolves this population. If all individuals have been tested,
	 * a new generation has been reached and mutation/crossover will
	 * be performed.
	 * 
	 * <p><b>This method only affects the population if trainMode is set to true.</b></p>
	 * 
	 * @return Whether the counter has been changed to the next individual
	 */
	public boolean evolve(MatchResult result, EvaluationResult evaluation) {
		boolean nextIndividual = false;
		
		if (trainMode) {
			boolean nextGeneration = false;
			int counterDelta = evaluation.getCounterDelta();
			put(result.getGenes(), evaluation.getFitness());
			
			if (counterDelta > 0) {
				counter += counterDelta;
				nextIndividual = true;
				nextGeneration = evaluation.shouldSkipToNextGeneration() || counter >= size();
				
				longestStreak = Math.max(longestStreak, streak);
				streak = 0;
			} else {
				streak++;
			}
			
			if (result.isWon()) {
				if (result.inGoal()) {
					int moves = result.getTurn();
					minGoalMoves = Math.min(moves, minGoalMoves);
					maxGoalMoves = Math.max(moves, maxGoalMoves);
					goalWins++;
				} else {
					wins++;
				}
			} else {
				losses++;
			}
			
			if (nextGeneration) {
				// Reached a full generation
				strategy.onPreNextGeneration(this);
				sortByFitnessDescending();
				maxFitness = individuals.getValue(0);
				
				counter = 0;
				streak = 0;
				generation++;
				
				log();
				copyMutate();
				saveAll();

				wins = 0;
				losses = 0;
				goalWins = 0;
				longestStreak = 0;
				minGoalMoves = Integer.MAX_VALUE;
				maxGoalMoves = Integer.MIN_VALUE;
				maxFitness = Float.NEGATIVE_INFINITY;
				strategy.onPostNextGeneration(this);
			}
		}
		
		return nextIndividual;
	}
	
	private void log() {
		GENETIC_LOG.info("");
		GENETIC_LOG.info(" <------------------ Generation {} ------------------> ", generation);
		GENETIC_LOG.info("{}", this);
		GENETIC_LOG.info("{} wins, {} goal wins, {} losses", new Object[] {wins, goalWins, losses});
		GENETIC_LOG.info("Min goal moves: {}, Max goal moves: {}, longest streak: {}", new Object[] {minGoalMoves, maxGoalMoves, longestStreak});
		GENETIC_LOG.info("");
	}
	
	/**
	 * @return The amount of individuals in this population.
	 */
	public int size() {
		return individuals.size();
	}
	
	private void saveCounter() {
		Path file = savePath.resolve(counterFile);
		
		try (OutputStream fos = Files.newOutputStream(file); DataOutputStream dos = new DataOutputStream(fos)) {
			dos.writeInt(counter);
			dos.writeInt(streak);
			dos.writeInt(generation);
			dos.writeInt(wins);
			dos.writeInt(goalWins);
			dos.writeInt(losses);
			dos.writeInt(minGoalMoves);
			dos.writeInt(maxGoalMoves);
			dos.writeInt(longestStreak);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private void saveStats() {
		Path file = savePath.resolve(statsFile);
		
		try {
			boolean fileExists = Files.exists(file);
			long fileSize = fileExists ? Files.size(file) : 0;
			
			if (!fileExists) {
				Files.createFile(file);
			}
			
			if (generation % 1000 == 0 && generation > 1 && fileExists && fileSize > maxStatsBytes) {
				// Truncate beginning of file to maxStatsBytes when file is becoming too large
				
				try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
					int oldBytes = (int) fileSize;
					int chunkSize = 7; // The number has to match the ints in a chunk written further below
					int chunkBytes = Integer.BYTES * chunkSize;
					int newBytes = maxStatsBytes - (maxStatsBytes % chunkSize);
					int truncatedBytes = oldBytes - newBytes;
					truncatedBytes -= truncatedBytes % chunkBytes;
					
					ByteBuffer buffer = ByteBuffer.allocate(oldBytes);
					channel.read(buffer);
					buffer.position(truncatedBytes);
					ByteBuffer result = buffer.slice();
					channel.position(0);
					channel.write(result);
					channel.truncate(newBytes);
				}
			}
			
			try (OutputStream fos = Files.newOutputStream(file, StandardOpenOption.APPEND);
					DataOutputStream dos = new DataOutputStream(fos)) {
				// Writes a chunk
				
				dos.writeInt(wins);
				dos.writeInt(goalWins);
				dos.writeInt((int) maxFitness);
				dos.writeInt(losses);
				dos.writeInt(minGoalMoves);
				dos.writeInt(maxGoalMoves);
				dos.writeInt(longestStreak);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private boolean loadCounter() {
		Path file = savePath.resolve(counterFile);
		
		try (InputStream fis = Files.newInputStream(file); DataInputStream dis = new DataInputStream(fis)) {
			counter = dis.readInt();
			streak = readIntOrZero(dis);
			generation = readIntOrZero(dis);
			wins = readIntOrZero(dis);
			goalWins = readIntOrZero(dis);
			losses = readIntOrZero(dis);
			minGoalMoves = readIntOrZero(dis);
			maxGoalMoves = readIntOrZero(dis);
			longestStreak = readIntOrZero(dis);
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}

	private int readIntOrZero(DataInputStream dis) throws IOException {
		return dis.available() > Integer.BYTES ? dis.readInt() : 0;
	}

	private void save(int index, float[] individual, float fitness) {
		Path file = savePath.resolve(individualFilePrefix + index);
		
		try (OutputStream fos = Files.newOutputStream(file); DataOutputStream dos = new DataOutputStream(fos)) {
			dos.writeFloat(fitness);
			for (float gene : individual) {
				dos.writeFloat(gene);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void saveAll() {
		int i = 0;
		for (float[] individual : individuals.keyList()) {
			save(i, individual, individuals.get(individual));
			i++;
		}

		saveCounter();
		saveStats();
		
		if (generation > 200 && generation % 100 == 0) {
			createBackup(i);
		}
	}
	
	private void createBackup(int size) {
		Path backupPath = savePath.resolve(backupFolder);
		
		try {
			if (!Files.exists(backupPath)) {
				Files.createDirectory(backupPath);
			}
			
			Files.copy(
					savePath.resolve(counterFile),
					backupPath.resolve(counterFile),
					StandardCopyOption.REPLACE_EXISTING
			);
			
			for (int i=0; i<size; i++) {
				Files.copy(
						savePath.resolve(individualFilePrefix + i),
						backupPath.resolve(individualFilePrefix + i),
						StandardCopyOption.REPLACE_EXISTING
				);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private boolean loadAll(int total) {
		for (int index=0; index<total; index++) {
			Path file = savePath.resolve(individualFilePrefix + index);
			
			if (!Files.exists(file)) {
				return false;
			}
			
			try (InputStream fis = Files.newInputStream(file); DataInputStream dis = new DataInputStream(fis)) {
				FloatList individual = new FloatList();
				float fitness = dis.readFloat();
				
				while (dis.available() > 0) {
					individual.add(dis.readFloat());
				}
				
				individuals.put(index, individual.toArray(), fitness);
			} catch (IOException e) {
				put(index, spawner.get(), Float.NEGATIVE_INFINITY);
			}
		}
		
		if (!loadCounter()) {
			saveCounter();
		}
		
		return true;
	}
	
	private void sortByFitnessDescending() {
		individuals.sortByValue((a, b) -> b.compareTo(a));
	}
	
	private void mutate(float[] individual, float[] target, int individualIndex, int targetIndex, Random random) {
		// Gaussian mutation
		
		for (int i=0; i<individual.length; i++) {
			float mutated;
			try {
				mutated = mutate(individual[i], random);
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new CorruptedDataException(individualIndex);
			}
			
			try {
				target[i] = mutated;
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new CorruptedDataException(targetIndex);
			}
		}
	}
	
	private float mutate(float x, Random random) {
		return x + ((float) random.nextGaussian() * mutatorWeight) + mutatorBias;
	}
	
	private void copyMutate() {
		Random random = ThreadLocalRandom.current();
		
		for (int i=0; i<survivorsPerGeneration; i++) {
			int targetIndex = survivorsPerGeneration + i;
			float[] source = individuals.getKey(i);
			float[] target = individuals.getKey(targetIndex);
			
			mutate(source, target, i, targetIndex, random);
			individuals.setValue(targetIndex, Float.NEGATIVE_INFINITY);
		}
	}
	
	public int getCounter() {
		return counter;
	}
	
	public int getStreak() {
		return streak;
	}
	
	@Override
	public String toString() {
		return "[Population] " + individuals.valueList().toString();
	}
}
