/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2017
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.algorithm.outlier.distance.parallel;

import org.junit.Test;

import de.lmu.ifi.dbs.elki.algorithm.outlier.AbstractOutlierAlgorithmTest;
import de.lmu.ifi.dbs.elki.algorithm.outlier.distance.KNNOutlier;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;

/**
 * Tests the KNNOutlier algorithm.
 * 
 * @author Lucia Cichella
 * @since 0.4.0
 */
public class ParallelKNNOutlierTest extends AbstractOutlierAlgorithmTest {
  @Test
  public void testKNNOutlier() {
    Database db = makeSimpleDatabase(UNITTEST + "outlier-3d-3clusters.ascii", 960);

    // Parameterization
    ListParameterization params = new ListParameterization();
    params.addParameter(KNNOutlier.Parameterizer.K_ID, 1);

    // setup Algorithm
    ParallelKNNOutlier<DoubleVector> knnOutlier = ClassGenericsUtil.parameterizeOrAbort(ParallelKNNOutlier.class, params);
    testParameterizationOk(params);

    // run KNNOutlier on database
    OutlierResult result = knnOutlier.run(db);

    testSingleScore(result, 945, 0.4793554700168577);
    testAUC(db, "Noise", result, 0.991462962962963);
  }
}