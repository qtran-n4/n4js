/*
 * Copyright (c) 2018 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
"use strict";

module.exports = {
   "ArtifactId": "model.base.tests",
   "VendorId": "eu.numberfour",
   "ProjectName": "model.base.tests",
   "VendorName": "NumberFour AG",
   "ProjectType": "test",
   "TestedProjects": [
      "model.base"
   ],
   "ProjectVersion": "0.0.1-SNAPSHOT",
   "Output": "src-gen",
   "Sources": {
      "external": [],
      "nested": [
         "foo:bar"
      ]
   },
   "ProjectDependencies": [
      "npm-dep: >1.3 <2.7 || 5",
      "n4js.base",
      "model.base",
      "mangelhaft.assert:12 >23 || 3"
   ],
   "RequiredRuntimeLibraries": [
      "n4js-runtime-n4",
      "n4js-runtime-es2015",
      "n4js-runtime-esnext",
      "mangelhaft.mangeltypes"
   ]
};
