// Licensed to Elasticsearch B.V. under one or more contributor
// license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright
// ownership. Elasticsearch B.V. licenses this file to you under
// the Apache License, Version 2.0 (the "License"); you may
// not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package co.elastic.mock

/**
 * Mock Run class.
 */
public class RunMock implements Serializable {

  def building = false
  def description

  public RunMock(Map params = [:]) {
    this.building = params.building
  }

  public boolean isBuilding() {
    return building
  }

  public void doStop() {
    println 'Stopping...'
  }

  public void setDescription(description) {
    this.description = description
  }

  public Parent getParent() {
    return new Parent()
  }

  private class Parent implements Serializable {
    public Parent() { }

    public Map getTriggers() {
      return [:]
    }
  }
}
