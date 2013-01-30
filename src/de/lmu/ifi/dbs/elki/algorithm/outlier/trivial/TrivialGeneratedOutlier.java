package de.lmu.ifi.dbs.elki.algorithm.outlier.trivial;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.HashSet;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.synthetic.bymodel.GeneratorSingleCluster;
import de.lmu.ifi.dbs.elki.data.type.NoSupportedDataTypeException;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.ChiSquaredDistribution;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.Distribution;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.LessEqualConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;

/**
 * Extract outlier score from the model the objects were generated by.
 * 
 * This algorithm can only be applied to data that was freshly generated, to the
 * generator model information is still available.
 * 
 * @author Erich Schubert
 */
public class TrivialGeneratedOutlier extends AbstractAlgorithm<OutlierResult> implements OutlierAlgorithm {
  /**
   * Class logger
   */
  private static final Logging LOG = Logging.getLogger(TrivialGeneratedOutlier.class);

  /**
   * Expected share of outliers.
   */
  double expect = 0.01;

  /**
   * Constructor.
   * 
   * @param expect Expected share of outliers
   */
  public TrivialGeneratedOutlier(double expect) {
    super();
    this.expect = expect;
  }

  /**
   * Constructor.
   */
  public TrivialGeneratedOutlier() {
    this(0.01);
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD, new SimpleTypeInformation<>(Model.class), TypeUtil.GUESSED_LABEL);
  }

  @Override
  public OutlierResult run(Database database) {
    Relation<NumberVector<?>> vecs = database.getRelation(TypeUtil.NUMBER_VECTOR_FIELD);
    Relation<Model> models = database.getRelation(new SimpleTypeInformation<>(Model.class));
    // Prefer a true class label
    try {
      Relation<?> relation = database.getRelation(TypeUtil.CLASSLABEL);
      return run(models, vecs, relation);
    } catch (NoSupportedDataTypeException e) {
      // Otherwise, try any labellike.
      return run(models, vecs, database.getRelation(TypeUtil.GUESSED_LABEL));
    }
  }

  /**
   * Run the algorithm
   * 
   * @param models Model relation
   * @param vecs Vector relation
   * @param labels Label relation
   * @return Outlier result
   */
  public OutlierResult run(Relation<Model> models, Relation<NumberVector<?>> vecs, Relation<?> labels) {
    WritableDoubleDataStore scores = DataStoreUtil.makeDoubleStorage(models.getDBIDs(), DataStoreFactory.HINT_HOT);

    // Adjustment constant
    final double minscore = expect / (expect + 1);

    HashSet<GeneratorSingleCluster> generators = new HashSet<>();
    for (DBIDIter iditer = models.iterDBIDs(); iditer.valid(); iditer.advance()) {
      Model model = models.get(iditer);
      if (model instanceof GeneratorSingleCluster) {
        generators.add((GeneratorSingleCluster) model);
      }
    }
    if (generators.size() == 0) {
      LOG.warning("No generator models found for dataset - all points will be considered outliers.");
    }

    for (DBIDIter iditer = models.iterDBIDs(); iditer.valid(); iditer.advance()) {
      double score = 0.0;
      // Convert to a math vector
      Vector v = vecs.get(iditer).getColumnVector();
      for (GeneratorSingleCluster gen : generators) {
        Vector tv = v;
        // Transform backwards
        if (gen.getTransformation() != null) {
          tv = gen.getTransformation().applyInverse(v);
        }
        final int dim = tv.getDimensionality();
        double lensq = 0.0;
        int norm = 0;
        for (int i = 0; i < dim; i++) {
          Distribution dist = gen.getDistribution(i);
          if (dist instanceof NormalDistribution) {
            NormalDistribution d = (NormalDistribution) dist;
            double delta = (tv.get(i) - d.getMean()) / d.getStddev();
            lensq += delta * delta;
            norm += 1;
          }
        }
        if (norm > 0) {
          // The squared distances are ChiSquared distributed
          score = Math.max(score, 1 - ChiSquaredDistribution.cdf(lensq, norm));
        }
      }
      if (expect < 1) {
        // score inversion.
        score = expect / (expect + score);
        // adjust to 0 to 1 range:
        score = (score - minscore) / (1 - minscore);
      }
      scores.putDouble(iditer, score);
    }
    Relation<Double> scoreres = new MaterializedRelation<>("Model outlier scores", "model-outlier", TypeUtil.DOUBLE, scores, models.getDBIDs());
    OutlierScoreMeta meta = new ProbabilisticOutlierScore(0., 1.);
    return new OutlierResult(meta, scoreres);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Expected share of outliers
     */
    public static final OptionID EXPECT_ID = new OptionID("modeloutlier.expect", "Expected amount of outliers, for making the scores more intuitive. When the value is 1, the CDF will be given instead.");

    /**
     * Expected share of outliers
     */
    double expect;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      DoubleParameter expectP = new DoubleParameter(EXPECT_ID, 0.01);
      expectP.addConstraint(new GreaterConstraint(0.0));
      expectP.addConstraint(new LessEqualConstraint(1.0));
      if (config.grab(expectP)) {
        expect = expectP.getValue();
      }
    }

    @Override
    protected TrivialGeneratedOutlier makeInstance() {
      return new TrivialGeneratedOutlier(expect);
    }
  }
}
