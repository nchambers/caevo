package timesieve;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import timesieve.util.ClassifiedDatum;
import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.util.StringUtils;

/**
 * This class offers static functions for computing accuracy, precision, F1, etc.
 * Some of the code is based on old Stanford JavaNLP functions.
 *  
 * @author chambers
 */
public class Tester {

	/**
	 * Prints a confusion matrix summarizing the given results to the
	 * given writer. Note that <tt>System.out</tt> is not a
	 * PrintWriter but <tt>new PrintWriter(System.out, true)</tt> is
	 * (with auto-flushing enabled).  Accuracy is printed at the
	 * top. Rows are correct label, cols are predicted label.  Format
	 * taken from eval.pl script used with CS224N assignments. Labels
	 * are ordered by "natural order" (alphabetic, numeric, etc) for
	 * consistent presentation across several folds/permutations of
	 * data.  Therefore all labels must be <i>mutually comparable</i>.
	 * <p/>
	 * <p/>
	 * To improve legibility, the row labels are printed with a wider
	 * row label cell width, calculated internally, if the cell Width is very
	 * narrow.
	 *
	 * @param cellWidth specifies the max number of characters for each
	 *                  cell in the matrix -- labels will be trimmed and numbers will be
	 *                  padded. Using a cellWidth of 8 is sufficient for most cases.
	 *                  cellWidth must be at least 2 -- one column is for spaces between
	 *                  columns.
	 */
	public static void printConfusionMatrix(ClassifiedDatum[] results, PrintWriter out, int cellWidth) {
		if (cellWidth < 2) {
			throw new IllegalArgumentException("cellWidth too narrow");
		}
		DecimalFormat df = new DecimalFormat("#.000"); // for percent accuracy
		out.println("Score " + numCorrect(results) + " right out of " + results.length + " (" + df.format(accuracy(results) * 100) + "%).");
		out.println();
		out.println("Confusions: (rows correct answer, columns guesses)");
		out.println();

		// aggregates predicted and correct labels and sorts them for consistent presentation
		Set<String> allLabels = labels(results);
		allLabels.addAll(predictedLabels(results));
		List<String> labels = new ArrayList<String>(allLabels);
		Collections.sort(labels);

		// Calculate rowLabelCellWidth
		int rowLabelCellWidth = 0;
		for (String label : labels) {
			int thisLabelLeng = label.length();
			if (thisLabelLeng > rowLabelCellWidth) {
				rowLabelCellWidth = thisLabelLeng;
			}
		}
		int max = Math.min(cellWidth * 2, 7); // print 6 letters minimum
		if (rowLabelCellWidth < cellWidth) {
			rowLabelCellWidth = cellWidth;
		} else if (rowLabelCellWidth > max) {
			rowLabelCellWidth = max;
		}

		for (int i = 0; i < rowLabelCellWidth; i++) {
			out.print(' '); // skip row labels
		}
		for (Iterator<String> colIter = labels.iterator(); colIter.hasNext();) {
			out.print(StringUtils.padLeft(StringUtils.trim(colIter.next(), cellWidth - 1), cellWidth - 1));
			if (colIter.hasNext()) {
				out.print(" ");
			}
		}
		out.println();
		for (String correctLabel : labels) {
			out.print(StringUtils.pad(StringUtils.trim(correctLabel, rowLabelCellWidth - 1), rowLabelCellWidth));
			List<ClassifiedDatum> curResults = resultsWithLabel(results, correctLabel);
			for (Iterator<String> colIter = labels.iterator(); colIter.hasNext();) {
				// pull num predicted each as label for this correct label
				String predictedLabel = colIter.next();
				int count = numPredicted(curResults, predictedLabel);
				String temp = Integer.toString(count);
				if (temp.length() <= cellWidth - 1) {
					out.print(StringUtils.padLeft(count, cellWidth - 1));
				} else {
					StringBuffer tempSB = new StringBuffer();
					int k = 1;
					for (int p = 1; p < cellWidth; p++) {
						k *= 10;
					}
					while (tempSB.length() < cellWidth - 1 && count > k) {
						tempSB.append("#");
						k *= 2;
					}
					out.print(StringUtils.padLeft(tempSB, cellWidth - 1));
				}
				if (colIter.hasNext()) {
					out.print(" ");
				}
			}
			out.println();
		}
	}

	public static int numPredicted(List<ClassifiedDatum> results, String label) {
		int count = 0;
		for( ClassifiedDatum cd : results )
			if( cd.predictedLabel().equals(label) )
				count++;
		return count;		  
	}

	public static List<ClassifiedDatum> resultsWithLabel(ClassifiedDatum[] results, String label) {
		List<ClassifiedDatum> matched = new ArrayList<ClassifiedDatum>();
		for( ClassifiedDatum cd : results )
			if( cd.label().equals(label) )
				matched.add(cd);
		return matched;
	}

	public static Set<String> predictedLabels(ClassifiedDatum[] results) {
		Set<String> labels = new HashSet<String>();
		for( ClassifiedDatum cd : results )
			labels.add(cd.predictedLabel());
		return labels;
	}

	public static Set<String> labels(ClassifiedDatum[] results) {
		Set<String> labels = new HashSet<String>();
		for( ClassifiedDatum cd : results )
			labels.add(cd.label());
		return labels;
	}

	/**
	 * Compute standard accuracy over the classified datums.
	 */
	public static double numCorrect(timesieve.util.ClassifiedDatum[] results) {
		int correct = 0;
		for( timesieve.util.ClassifiedDatum result : results )
			if( result.isCorrect() )
				correct++;
		return correct;
	}

	/**
	 * Compute standard accuracy over the classified datums.
	 */
	public static double accuracy(timesieve.util.ClassifiedDatum[] results) {
		return (double)numCorrect(results) / (double)results.length;
	}

	/**
	 * Returns Precision/Recall/F1 stats for the results with respect to the
	 * given label. A true positive means both the predicted and correct
	 * label match the given label. A false positive means only the predicted
	 * label matches, and a false negative means that only the correct label
	 * matches. Datums in which neither label matches are disregarded as is
	 * standard.
	 */
	public static <L, F> PrecisionRecallStats precisionRecallStats(ClassifiedDatum[] results, L label) {
		PrecisionRecallStats stats = new PrecisionRecallStats();
		for (int i = 0; i < results.length; i++) {
			if (results[i].label().equals(label)) {
				if (results[i].isCorrect()) {
					stats.incrementTP(); // hit
				} else {
					stats.incrementFN(); // miss
				}
			} else if (results[i].predictedLabel().equals(label)) {
				stats.incrementFP(); // false alarm
			}
		}
		return (stats);
	}
}
