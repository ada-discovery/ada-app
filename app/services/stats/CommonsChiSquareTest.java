package services.stats;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.exception.*;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Implements Chi-Square test statistics.
 *
 * <p>This implementation handles both known and unknown distributions.</p>
 *
 * <p>Two samples tests can be used when the distribution is unknown <i>a priori</i>
 * but provided by one sample, or when the hypothesis under test is that the two
 * samples come from the same underlying distribution.</p>
 *
 */
public class CommonsChiSquareTest {

    private static final double DEFAULT_EPSILON = 1e-100;
    private static final MathContext mc = new MathContext(200, RoundingMode.FLOOR);

    /**
     * Construct a ChiSquareTest
     */
    public CommonsChiSquareTest() {
        super();
    }

    /**
     *  Computes the Chi-Square statistic associated with a
     * <a href="http://www.itl.nist.gov/div898/handbook/prc/section4/prc45.htm">
     *  chi-square test of independence</a> based on the input <code>counts</code>
     *  array, viewed as a two-way table.
     * <p>
     * The rows of the 2-way table are
     * <code>count[0], ... , count[count.length - 1] </code></p>
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>All counts must be &ge; 0.
     * </li>
     * <li>The count array must be rectangular (i.e. all count[i] subarrays
     *  must have the same length).
     * </li>
     * <li>The 2-way table represented by <code>counts</code> must have at
     *  least 2 columns and at least 2 rows.
     * </li>
     * </li></ul></p><p>
     * If any of the preconditions are not met, an
     * <code>IllegalArgumentException</code> is thrown.</p>
     *
     * @param counts array representation of 2-way table
     * @return chiSquare test statistic
     * @throws NullArgumentException if the array is null
     * @throws DimensionMismatchException if the array is not rectangular
     * @throws NotPositiveException if {@code counts} has negative entries
     */
    public double chiSquare(final long[][] counts)
        throws NullArgumentException, NotPositiveException,
        DimensionMismatchException {

        checkArray(counts);
        int nRows = counts.length;
        int nCols = counts[0].length;

        // compute row, column and total sums
        double[] rowSum = new double[nRows];
        double[] colSum = new double[nCols];
        double total = 0.0d;
        for (int row = 0; row < nRows; row++) {
            for (int col = 0; col < nCols; col++) {
                rowSum[row] += counts[row][col];
                colSum[col] += counts[row][col];
                total += counts[row][col];
            }
        }

        // compute expected counts and chi-square
        double sumSq = 0.0d;
        double expected = 0.0d;
        for (int row = 0; row < nRows; row++) {
            for (int col = 0; col < nCols; col++) {
                expected = (rowSum[row] * colSum[col]) / total;
                sumSq += ((counts[row][col] - expected) *
                        (counts[row][col] - expected)) / expected;
            }
        }
        return sumSq;
    }

    /**
     * Returns the <i>observed significance level</i>, or <a href=
     * "http://www.cas.lancs.ac.uk/glossary_v1.1/hyptest.html#pvalue">
     * p-value</a>, associated with a
     * <a href="http://www.itl.nist.gov/div898/handbook/prc/section4/prc45.htm">
     * chi-square test of independence</a> based on the input <code>counts</code>
     * array, viewed as a two-way table.
     * <p>
     * The rows of the 2-way table are
     * <code>count[0], ... , count[count.length - 1] </code></p>
     * <p>
     * <strong>Preconditions</strong>: <ul>
     * <li>All counts must be &ge; 0.
     * </li>
     * <li>The count array must be rectangular (i.e. all count[i] subarrays must have
     *     the same length).
     * </li>
     * <li>The 2-way table represented by <code>counts</code> must have at least 2
     *     columns and at least 2 rows.
     * </li>
     * </li></ul></p><p>
     * If any of the preconditions are not met, an
     * <code>IllegalArgumentException</code> is thrown.</p>
     *
     * @param counts array representation of 2-way table
     * @return p-value
     * @throws NullArgumentException if the array is null
     * @throws DimensionMismatchException if the array is not rectangular
     * @throws NotPositiveException if {@code counts} has negative entries
     * @throws MaxCountExceededException if an error occurs computing the p-value
     */
    public ChiSquareResult chiSquareTest(final long[][] counts)
        throws NullArgumentException, DimensionMismatchException,
        NotPositiveException, MaxCountExceededException {

        checkArray(counts);

        double statistics = chiSquare(counts);

        double df = ((double) counts.length -1) * ((double) counts[0].length - 1);
        double shape = df / 2;
        double scale = 2;

        BigDecimal pValue = BigDecimal.ONE.subtract(cumulativeProbability(shape, scale, statistics));
        return ChiSquareResult$.MODULE$.apply(pValue.doubleValue(), (int) df, statistics);
    }

    public BigDecimal cumulativeProbability(double shape, double scale, double x) {
        BigDecimal ret;

        if (x <= 0) {
            ret = BigDecimal.ZERO;
        } else {
            ret = regularizedGammaP(shape, x / scale, DEFAULT_EPSILON, Integer.MAX_VALUE);
        }

        return ret;
    }

    /**
     * Returns the regularized gamma function P(a, x).
     *
     * The implementation of this method is based on:
     * <ul>
     *  <li>
     *   <a href="http://mathworld.wolfram.com/RegularizedGammaFunction.html">
     *   Regularized Gamma Function</a>, equation (1)
     *  </li>
     *  <li>
     *   <a href="http://mathworld.wolfram.com/IncompleteGammaFunction.html">
     *   Incomplete Gamma Function</a>, equation (4).
     *  </li>
     *  <li>
     *   <a href="http://mathworld.wolfram.com/ConfluentHypergeometricFunctionoftheFirstKind.html">
     *   Confluent Hypergeometric Function of the First Kind</a>, equation (1).
     *  </li>
     * </ul>
     *
     * @param a the a parameter.
     * @param x the value.
     * @param epsilon When the absolute value of the nth item in the
     * series is less than epsilon the approximation ceases to calculate
     * further elements in the series.
     * @param maxIterations Maximum number of "iterations" to complete.
     * @return the regularized gamma function P(a, x)
     * @throws MaxCountExceededException if the algorithm fails to converge.
     */
    public static BigDecimal regularizedGammaP(double a,
                                           double x,
                                           double epsilon,
                                           int maxIterations) {
        BigDecimal ret;

        if (Double.isNaN(a) || Double.isNaN(x) || (a <= 0.0) || (x < 0.0)) {
            ret = null;
        } else if (x == 0.0) {
            ret = BigDecimal.ZERO;
        } else if (x >= a + 1) {
            // use regularizedGammaQ because it should converge faster in this
            // case.
            double gammaQ = Gamma.regularizedGammaQ(a, x, epsilon, maxIterations);
            ret = BigDecimal.ONE.subtract(new BigDecimal(gammaQ, mc));
        } else {
            // calculate series
            double n = 0.0; // current element index
            double an = 1.0 / a; // n-th element in the series
            double sum = an; // partial sum

            while (FastMath.abs(an/sum) > epsilon &&
                    n < maxIterations &&
                    sum < Double.POSITIVE_INFINITY) {
                // compute next element in the series
                n += 1.0;
                an *= x / (a + n);

                // update partial sum
                sum += an;
            }
            if (n >= maxIterations) {
                throw new MaxCountExceededException(maxIterations);
            } else if (Double.isInfinite(sum)) {
                ret = BigDecimal.ONE;
            } else {
                double result = FastMath.exp(-x + (a * FastMath.log(x)) - Gamma.logGamma(a)) * sum;
                ret = new BigDecimal(result, mc);
            }
        }

        return ret;
    }

    /**
     * Checks to make sure that the input long[][] array is rectangular,
     * has at least 2 rows and 2 columns, and has all non-negative entries.
     *
     * @param in input 2-way table to check
     * @throws NullArgumentException if the array is null
     * @throws DimensionMismatchException if the array is not valid
     * @throws NotPositiveException if the array contains any negative entries
     */
    private void checkArray(final long[][] in)
        throws NullArgumentException, DimensionMismatchException,
        NotPositiveException {

        if (in.length < 2) {
            throw new DimensionMismatchException(in.length, 2);
        }

        if (in[0].length < 2) {
            throw new DimensionMismatchException(in[0].length, 2);
        }

        MathArrays.checkRectangular(in);
        MathArrays.checkNonNegative(in);
    }
}
