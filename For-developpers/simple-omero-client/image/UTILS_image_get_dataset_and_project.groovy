#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id


/* Code description
 *  
 * This script gets the parent dataset / project from an given image
 *  
 *
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.09.01
 * Version: 1.0.0
 *
 * -----------------------------------------------------------------------------
 * Copyright (c) 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * All rights reserved.
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * -----------------------------------------------------------------------------
 */

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected "+ host
	
	try{
		// Create the image Wrapper object
		ImageWrapper image_wpr = user_client.getImage(id)
		
		// Get dataset information from the image ID
		List<DatasetWrapper> dataset_wpr_list = image_wpr.getDatasets(user_client)
		
		if(!dataset_wpr_list.isEmpty()){
			dataset_wpr_list.each{println("dataset name : "+it.getName()+", dataset id : "+it.getId())};
			
			// Get dataset information from the image ID
			List<ProjectWrapper> project_wpr_list = image_wpr.getProjects(user_client)
			project_wpr_list.each{println("Project name : "+it.getName()+", project id : "+it.getId())};
		}
		else{
			println "Warning : Your image is part of a plate/screen, not part of a dataset/project"
		}
		
	} finally {
		user_client.disconnect()
		println "Disconnected from "+host
	}
} else {
	println "Not able to connect to "+host
}
return


/*
 * Imports
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import omero.gateway.model.DatasetData;