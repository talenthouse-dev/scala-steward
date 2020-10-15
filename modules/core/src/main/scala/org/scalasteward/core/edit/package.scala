/*
 * Copyright 2018-2020 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core

import better.files.File
import cats.implicits._
import org.scalasteward.core.data.{Dependency, GroupId, Update}
import org.scalasteward.core.util.Change.Unchanged
import org.scalasteward.core.util.{Change, Nel}
import scala.collection.mutable
import scala.util.matching.Regex

package object edit {

  /** Like `replaceSomeInChange` but does not change anything between
    * `scala-steward:off` and `scala-steward:on` markers.
    */
  def replaceSomeInAllowedParts(
      regex: Regex,
      target: CharSequence,
      replacer: Regex.Match => Option[String]
  ): Change[String] =
    splitByOffOnMarker(target.toString).foldMap {
      case (part, true)  => util.string.replaceSomeInChange(regex, part, replacer)
      case (part, false) => Unchanged(part)
    }

  private[edit] def splitByOffOnMarker(target: String): Nel[(String, Boolean)] =
    if (!target.contains("scala-steward:off"))
      Nel.of((target, true))
    else {
      val buffer = mutable.ListBuffer.empty[(String, Boolean)]
      val on = new StringBuilder()
      val off = new StringBuilder()
      val regexIgnoreMultiLinesBegins = "^\\s*//\\s*scala-steward:off".r
      def flush(builder: StringBuilder, canReplace: Boolean): Unit =
        if (builder.nonEmpty) {
          buffer.append((builder.toString(), canReplace))
          builder.clear()
        }
      target.linesWithSeparators.foreach { line =>
        if (off.nonEmpty)
          if (line.contains("scala-steward:on")) {
            flush(off, false)
            on.append(line)
          } else
            off.append(line)
        else if (line.contains("scala-steward:off")) {
          flush(on, true)
          if (regexIgnoreMultiLinesBegins.findFirstIn(line).isDefined)
            off.append(line)
          else
            // single line off
            buffer.append((line, false))
        } else on.append(line)
      }
      flush(on, true)
      flush(off, false)
      Nel.fromListUnsafe(buffer.toList)
    }

  def fileAllowedToEdit(update: Update, defaultExtensions: Set[String])(file: File): Boolean =
    !file.toString.contains(".git/") &&
      update.dependencies.exists(pathAllowedToEdit(_, defaultExtensions)(file.toString))

  def pathAllowedToEdit(dep: Dependency, defaultExtensions: Set[String])(path: String): Boolean =
    matchesExtension(path, allowedExtensions(dep, defaultExtensions))

  private def matchesExtension(path: String, extensions: Set[String]): Boolean =
    extensions.exists(path.endsWith)

  private def allowedExtensions(dep: Dependency, defaultExtensions: Set[String]): Set[String] =
    () match {
      case _ if isSbt(dep)          => Set("build.properties")
      case _ if isScalafmtCore(dep) => Set(".scalafmt.conf")
      case _                        => defaultExtensions
    }

  private def isSbt(dep: Dependency): Boolean =
    dep.groupId === GroupId("org.scala-sbt") && dep.artifactId.name === "sbt"

  private def isScalafmtCore(dep: Dependency): Boolean =
    dep.groupId === GroupId("org.scalameta") && dep.artifactId.name === "scalafmt-core"
}
