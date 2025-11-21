package src.labs.zombayes.models;


// SYSTEM IMPORTS
import edu.bu.labs.zombayes.agents.SurvivalAgent;
import edu.bu.labs.zombayes.linalg.Matrix;
import edu.bu.labs.zombayes.linalg.Functions;
import edu.bu.labs.zombayes.utils.Pair;
import edu.bu.labs.zombayes.features.Features.FeatureType;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// JAVA PROJECT IMPORTS


public class NaiveBayes
    extends Object
{
    //
    // FIELDS
    //

    // (FEATURE_TYPE, NUM_FEATURE_VALUES)
    private final List<Pair<FeatureType, Integer>> featureHeader;

    // P(class)
    private Map<Integer, Double> classPriors;

    // For each class: list indexed by featureIdx -> map: value -> P(x_j = value | class)
    private Map<Integer, List<Map<Integer, Double>>> discreteProbabilities;

    // For continuous features:
    // For each class: list indexed by featureIdx -> mean / stdDev for that feature & class
    private Map<Integer, List<Double>> continuousMeans;
    private Map<Integer, List<Double>> continuousStdDevs;

    // Laplace smoothing for discrete features
    private final double smoothingAlpha = 1.0;


    //
    // CONSTRUCTOR(S)
    //

    public NaiveBayes(List<Pair<FeatureType, Integer>> featureHeader)
    {
        this.featureHeader        = featureHeader;
        this.classPriors          = new HashMap<>();
        this.discreteProbabilities = new HashMap<>();
        this.continuousMeans      = new HashMap<>();
        this.continuousStdDevs    = new HashMap<>();
    }

    //
    // GET/SET
    //

    public List<Pair<FeatureType, Integer>> getFeatureHeader() { return this.featureHeader; }


    //
    // METHODS
    //

    // Train the Naive Bayes model on X (N x D) and y_gt (N x 1)
    public void fit(Matrix X, Matrix y_gt)
    {
        int totalExamples = X.getShape().getNumRows();   // # rows = # samples
        int numFeatures   = this.featureHeader.size();

        // ----- 1. Count class occurrences -----
        Map<Integer, Integer> classCounts = new HashMap<>();

        for (int i = 0; i < totalExamples; i++) {
            int classLabel = (int) Math.round(y_gt.get(i, 0));
            classCounts.put(classLabel, classCounts.getOrDefault(classLabel, 0) + 1);
        }

        // ----- 2. Compute class priors P(class) -----
        this.classPriors.clear();
        for (Map.Entry<Integer, Integer> entry : classCounts.entrySet()) {
            int label   = entry.getKey();
            int count   = entry.getValue();
            double prior = (double) count / (double) totalExamples;
            this.classPriors.put(label, prior);
        }

        // ----- 3. Prepare data structures per class -----
        this.discreteProbabilities.clear();
        this.continuousMeans.clear();
        this.continuousStdDevs.clear();

        for (int classLabel : classCounts.keySet()) {

            // For each class, we want lists indexed by featureIdx (0..numFeatures-1)
            List<Map<Integer, Double>> discList = new ArrayList<>(numFeatures);
            List<Double> meanList               = new ArrayList<>(numFeatures);
            List<Double> stdList                = new ArrayList<>(numFeatures);

            // initialize with nulls / zeros so featureIdx is aligned
            for (int j = 0; j < numFeatures; j++) {
                discList.add(null);
                meanList.add(0.0);
                stdList.add(1.0);
            }

            this.discreteProbabilities.put(classLabel, discList);
            this.continuousMeans.put(classLabel, meanList);
            this.continuousStdDevs.put(classLabel, stdList);
        }

        // ----- 4. Process each feature -----
        for (int featureIdx = 0; featureIdx < numFeatures; featureIdx++) {
            Pair<FeatureType, Integer> featureInfo = this.featureHeader.get(featureIdx);
            FeatureType type = featureInfo.getFirst();

            if (type == FeatureType.DISCRETE) {
                int numValues = featureInfo.getSecond();
                processDiscreteFeature(X, y_gt, featureIdx, numValues, classCounts);
            } else if (type == FeatureType.CONTINUOUS) {
                processContinuousFeature(X, y_gt, featureIdx, classCounts);
            }
        }
    }

    // Handle a DISCRETE feature j: estimate P(x_j = v | class) with Laplace smoothing
    private void processDiscreteFeature(Matrix X,
                                        Matrix y_gt,
                                        int featureIdx,
                                        int numValues,
                                        Map<Integer, Integer> classCounts)
    {
        int totalExamples = X.getShape().getNumRows();

        for (int classLabel : this.classPriors.keySet()) {
            // Count occurrences of each feature value in this class
            Map<Integer, Integer> valueCounts = new HashMap<>();
            int classTotal = 0;

            for (int i = 0; i < totalExamples; i++) {
                int label = (int) Math.round(y_gt.get(i, 0));
                if (label == classLabel) {
                    int featureValue = (int) Math.round(X.get(i, featureIdx));
                    valueCounts.put(featureValue,
                            valueCounts.getOrDefault(featureValue, 0) + 1);
                    classTotal++;
                }
            }

            // Convert counts to probabilities with Laplace smoothing
            Map<Integer, Double> probabilities = new HashMap<>();
            double denom = classTotal + this.smoothingAlpha * numValues;

            for (int value = 0; value < numValues; value++) {
                int count = valueCounts.getOrDefault(value, 0);
                double prob = (count + this.smoothingAlpha) / denom;
                probabilities.put(value, prob);
            }

            // Store at the correct feature index
            this.discreteProbabilities.get(classLabel).set(featureIdx, probabilities);
        }
    }

    // Handle a CONTINUOUS feature j: estimate mean and stdDev for each class
    private void processContinuousFeature(Matrix X,
                                          Matrix y_gt,
                                          int featureIdx,
                                          Map<Integer, Integer> classCounts)
    {
        int totalExamples = X.getShape().getNumRows();

        for (int classLabel : this.classPriors.keySet()) {
            List<Double> values = new ArrayList<>();

            // Collect values for this feature and class
            for (int i = 0; i < totalExamples; i++) {
                int label = (int) Math.round(y_gt.get(i, 0));
                if (label == classLabel) {
                    values.add(X.get(i, featureIdx));
                }
            }

            // Compute mean
            double sum = 0.0;
            for (double v : values) {
                sum += v;
            }
            double mean = values.isEmpty() ? 0.0 : (sum / values.size());

            // Compute variance
            double varSum = 0.0;
            for (double v : values) {
                double diff = v - mean;
                varSum += diff * diff;
            }
            double variance = values.isEmpty() ? 0.0 : (varSum / values.size());
            double stdDev   = Math.sqrt(variance);

            if (stdDev == 0.0) {
                stdDev = 1e-6; // avoid divide-by-zero
            }

            this.continuousMeans.get(classLabel).set(featureIdx, mean);
            this.continuousStdDevs.get(classLabel).set(featureIdx, stdDev);
        }
    }

    // Gaussian pdf for continuous features: P(x | mean, stdDev)
    private double gaussianPDF(double x, double mean, double stdDev)
    {
        double diff = x - mean;
        double exponent = Math.exp(- (diff * diff) / (2.0 * stdDev * stdDev));
        return (1.0 / (stdDev * Math.sqrt(2.0 * Math.PI))) * exponent;
    }

    // Predict class label for a single row-vector x (1 x D)
    public int predict(Matrix x)
    {
        int numFeatures = this.featureHeader.size();

        // log P(class | x) up to additive constant
        Map<Integer, Double> logProbabilities = new HashMap<>();

        // Initialize with log priors
        for (int classLabel : this.classPriors.keySet()) {
            double prior = this.classPriors.get(classLabel);
            if (prior <= 0.0) {
                prior = 1e-12;
            }
            logProbabilities.put(classLabel, Math.log(prior));
        }

        // For each feature, add log P(x_j | class)
        for (int featureIdx = 0; featureIdx < numFeatures; featureIdx++) {
            Pair<FeatureType, Integer> featureInfo = this.featureHeader.get(featureIdx);
            FeatureType type = featureInfo.getFirst();
            double featureValue = x.get(0, featureIdx); // assuming row vector

            for (int classLabel : this.classPriors.keySet()) {
                double featureProb;

                if (type == FeatureType.DISCRETE) {
                    int discreteValue = (int) Math.round(featureValue);
                    int numValues     = featureInfo.getSecond();

                    // Clamp to legal range just in case
                    if (discreteValue < 0) {
                        discreteValue = 0;
                    } else if (discreteValue >= numValues) {
                        discreteValue = numValues - 1;
                    }

                    Map<Integer, Double> probs =
                        this.discreteProbabilities.get(classLabel).get(featureIdx);

                    // If something is missing, give a tiny probability instead of 0
                    if (probs == null) {
                        featureProb = 1e-12;
                    } else {
                        featureProb = probs.getOrDefault(discreteValue, 1e-12);
                    }

                } else { // CONTINUOUS
                    double mean   = this.continuousMeans.get(classLabel).get(featureIdx);
                    double stdDev = this.continuousStdDevs.get(classLabel).get(featureIdx);
                    featureProb   = gaussianPDF(featureValue, mean, stdDev);
                }

                if (featureProb <= 0.0) {
                    featureProb = 1e-12;
                }

                double oldLog = logProbabilities.get(classLabel);
                logProbabilities.put(classLabel, oldLog + Math.log(featureProb));
            }
        }

        // Pick the class with maximum log probability
        int bestLabel = -1;
        double bestLog = Double.NEGATIVE_INFINITY;

        for (Map.Entry<Integer, Double> entry : logProbabilities.entrySet()) {
            int label   = entry.getKey();
            double logP = entry.getValue();
            if (logP > bestLog || bestLabel == -1) {
                bestLog = logP;
                bestLabel = label;
            }
        }

        return bestLabel;
    }

}
