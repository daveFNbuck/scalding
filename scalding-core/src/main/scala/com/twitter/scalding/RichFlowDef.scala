/*
Copyright 2014 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.scalding

import cascading.flow.FlowDef
import cascading.pipe.Pipe

/**
 * This is an enrichment-pattern class for cascading.flow.FlowDef.
 * The rule is to never use this class directly in input or return types, but
 * only to add methods to FlowDef.
 */
class RichFlowDef(val fd: FlowDef) {
  // RichPipe and RichFlowDef implicits
  import Dsl._

  def copy: FlowDef = {
    val newFd = new FlowDef
    newFd.merge(fd)
    newFd
  }

  private[scalding] def mergeMisc(o: FlowDef) {
    fd.addTags(o.getTags)
    fd.addTraps(o.getTraps)
    fd.addCheckpoints(o.getCheckpoints)
    fd.setAssertionLevel(o.getAssertionLevel)
    fd.setName(o.getName)
    fd
  }

  /**
   * Mutate current flow def to add all sources/sinks/etc from given FlowDef
   */
  def merge(o: FlowDef): FlowDef = {
    fd.addSources(o.getSources)
    fd.addSinks(o.getSinks)
    fd.addTails(o.getTails)
    fd.mergeMisc(o)
    fd
  }

  /**
   * New flow def with only sources upstream from tails.
   */
  def withoutUnusedSources: FlowDef = {
    import collection.JavaConverters._

    // find all heads reachable from the tails (as a set of names)
    val heads = fd.getTails.asScala
      .flatMap(_.getHeads).map(_.getName).toSet
    // add taps associated with heads to localFlow
    val filteredSources = fd.getSources.asScala.filterKeys(src => heads.contains(src)).asJava

    val newFd = fd.copy
    newFd.getSources.clear()
    newFd.addSources(filteredSources)

    newFd
  }

  /**
   * FlowDef that only includes things upstream from the given Pipe
   */
  def onlyUpstreamFrom(pipe: Pipe): FlowDef = {
    val newFd = new FlowDef
    // don't copy any sources/sinks
    newFd.mergeMisc(fd)

    val sourceTaps = fd.getSources
    val newSrcs = newFd.getSources

    pipe.upstreamPipes
      .filter(_.getPrevious.length == 0) // implies _ is a head
      .foreach { head =>
        if (!newSrcs.containsKey(head.getName))
          newFd.addSource(head, sourceTaps.get(head.getName))
      }

    val sinks = fd.getSinks
    if (sinks.containsKey(pipe.getName)) {
      newFd.addTailSink(pipe, sinks.get(pipe.getName))
    }

    newFd
  }

}
