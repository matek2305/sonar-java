/*
 * SonarQube Java
 * Copyright (C) 2010-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.jacoco;

import org.apache.commons.lang.BooleanUtils;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.data.ISessionInfoVisitor;
import org.jacoco.core.data.SessionInfo;
import org.sonar.squidbridge.api.AnalysisException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Utility class to merge JaCoCo reports.
 *
 * This class handles two versions of JaCoCo binary format to merge.
 */
public final class JaCoCoReportMerger {

  private JaCoCoReportMerger() {
  }

  /**
   * Merge all reports in reportOverall.
   * @param reportOverall destination file of merge.
   * @param reports files to be merged.
   */
  public static void mergeReports(File reportOverall, File... reports) {
    ExecutionDataVisitor edv = new ExecutionDataVisitor();
    boolean isCurrentVersionFormat = loadSourceFiles(edv, reports);

    try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(reportOverall))) {
      Object visitor;
      if (isCurrentVersionFormat) {
        visitor = new ExecutionDataWriter(outputStream);
      } else {
        visitor = new org.jacoco.previous.core.data.ExecutionDataWriter(outputStream);
      }
      for (Map.Entry<String, ExecutionDataStore> entry : edv.getSessions().entrySet()) {
        SessionInfo sessionInfo = new SessionInfo(entry.getKey(), 0, 0);
        ((ISessionInfoVisitor) visitor).visitSessionInfo(sessionInfo);
        entry.getValue().accept((IExecutionDataVisitor) visitor);
      }
    } catch (IOException e) {
      throw new AnalysisException(String.format("Unable to write overall coverage report %s", reportOverall.getAbsolutePath()), e);
    }
  }

  private static boolean loadSourceFiles(ExecutionDataVisitor executionDataVisitor, File... reports) {
    Boolean isCurrentVersionFormat = null;
    for (File report : reports) {
      if (report.isFile()) {
        JacocoReportReader jacocoReportReader = new JacocoReportReader(report).readJacocoReport(executionDataVisitor, executionDataVisitor);
        boolean reportFormatIsCurrent = jacocoReportReader.useCurrentBinaryFormat();
        if (isCurrentVersionFormat == null) {
          isCurrentVersionFormat = reportFormatIsCurrent;
        } else if (!isCurrentVersionFormat.equals(reportFormatIsCurrent)) {
          throw new IllegalStateException("You are trying to merge two different JaCoCo binary formats. Please use only one version of JaCoCo.");
        }
      }
    }
    return BooleanUtils.isNotFalse(isCurrentVersionFormat);
  }

}
