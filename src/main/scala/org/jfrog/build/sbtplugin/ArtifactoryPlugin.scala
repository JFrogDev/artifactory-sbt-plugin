/*
 * Copyright (C) 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package  org.jfrog.build.sbtplugin

import sbt._
import sbt.Keys._
import org.jfrog.build.client.ArtifactoryClientConfiguration
import org.jfrog.build.api.{
	Module,
	Build,
	Artifact,
	Dependency
}
import org.jfrog.build.api.util.{
	NullLog,
	DeployableFile
}

object ArtifactoryKeys {
	val artifactory = settingKey[ArtifactoryClientConfiguration]("An api to collect/store published artifact information.")
	val artifactoryRecordInfo = taskKey[ArtifactoryModule]("") //TODO: provide a description of this
	val artifactoryPublish = taskKey[Unit]("publishing all files to artifactory.")
}

import ArtifactoryKeys._
object ArtifactoryPlugin extends AutoPlugin {
	override def trigger = allRequirements
	override def requires = sbt.plugins.IvyPlugin
	val autoImport = ArtifactoryKeys

	override def projectSettings: Seq[Setting[_]] = 
	  Seq(
        resolvers := {
        	SbtExtractor.defineResolvers(artifactory.value.resolver) match {
        		case Nil => resolvers.value
        		case stuff => stuff
        	}
        },
        artifactoryRecordInfo :=  {
        	SbtExtractor.extractModule(streams.value.log, packagedArtifacts.value, update.value, projectID.value, artifactory.value)
        },
        artifactoryPublish := (artifactoryPublish in Global).value,
        aggregate in artifactoryPublish := false
	  )

	override def globalSettings: Seq[Setting[_]] =
	  Seq(
	  	artifactory := {
	  		val config = new ArtifactoryClientConfiguration(new NullLog) //TODO: is NullLog really acceptable here?  If we had a project, this would be set with an IvyBuildInfoLog as per Ivy-extractor what about the report?
        config.info.setBuildStarted(System.currentTimeMillis())
	  		config
	  	},
	  	artifactoryPublish := {
	  		SbtExtractor.publish(streams.value.log, artifactory.value, recordAllTasksEverywhere.value)
	  	}
	  )

   //  A dynamic task which looks up the artifactoryRecordInfo task on ALL
   //  possible projects and joins the results together.
   lazy val recordAllTasksEverywhere = Def.taskDyn {
   	  val refs = buildStructure.value.allProjectRefs  //It seems like possibly this line builds the pom file?
      //TODO: if we have a POM file should we be basing this on IVY or Maven?  what is the build structure like?
   	  joinAllExistingTasks(refs, artifactoryRecordInfo)
   }

   // Joins all tasks in a project, returns the results in a sequence.
   // If a project does not have a task, that project's task result does not show up in
   // the seuqence.
   def joinAllExistingTasks[T](refs: Seq[ProjectRef], key: TaskKey[T]): Def.Initialize[Task[Seq[T]]] = {
   	 val taskInitializers: Seq[Def.Initialize[Task[Seq[T]]]] =
   	   for {
   	   	 ref <- refs
   	   	 init = (key in ref).?
   	   } yield init map (_.toSeq)
   	 taskInitializers reduce { (lhs, rhs) =>
   	 	lhs.zipWith(rhs) { (task, task2) =>
   	 		for {
   	 			result <- task
   	 			result2 <- task2
   	 		} yield result ++ result2
   	 	}
   	 }
   }
}