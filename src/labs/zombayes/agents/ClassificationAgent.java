package src.labs.zombayes.agents;


// SYSTEM IMPORTS
import edu.bu.labs.zombayes.agents.SurvivalAgent;
import edu.bu.labs.zombayes.features.Features.FeatureType;
import edu.bu.labs.zombayes.linalg.Matrix;
import edu.bu.labs.zombayes.utils.Pair;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS
import src.labs.zombayes.models.NaiveBayes;



public class ClassificationAgent
    extends SurvivalAgent
{

    private NaiveBayes model;

    public ClassificationAgent(int playerNum, String[] args)
    {
        super(playerNum, args);

        List<Pair<FeatureType, Integer> > featureHeader = new ArrayList<>(4);
        featureHeader.add(new Pair<>(FeatureType.CONTINUOUS, -1));
        featureHeader.add(new Pair<>(FeatureType.CONTINUOUS, -1));
        featureHeader.add(new Pair<>(FeatureType.DISCRETE, 3));
        featureHeader.add(new Pair<>(FeatureType.DISCRETE, 4));

        this.model = new NaiveBayes(featureHeader);
    }

    public NaiveBayes getModel() { return this.model; }

    @Override
    public void train(Matrix X, Matrix y_gt)
    {
        System.out.println(X.getShape() + " " + y_gt.getShape());
        this.getModel().fit(X, y_gt);
    }

    @Override
    public int predict(Matrix featureRowVector)
    {
        return this.getModel().predict(featureRowVector);
    }

}