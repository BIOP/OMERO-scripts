#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id


/* Code description 
 *
 * Unlink all tags linked to the selected container
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.05.18
 * Version: 1.0.3
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
 * 
 * History
 * - 2023.06.19 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3 + remove unnecessary imports
 * + turn the deletion into unlinking
 * - 2023.10.17 : Add popup message at the end of the script and if an error occurs while running
 * - 2023.11.06 : Remove popup messages from template
 */

/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
 * 
 */
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		def tags
		switch (object_type){
			case "image":	
				tags = unlinkAllTagsOnImage(user_client, user_client.getImage(id))
				break	
			case "dataset":
				tags = unlinkAllTagsOnImage(user_client, user_client.getDataset(id))
				break
			case "project":
				tags = unlinkAllTagsOnImage(user_client, user_client.getProject(id))
				break
			case "well":
				tags = unlinkAllTagsOnImage(user_client, user_client.getWells(id))
				break
			case "plate":
				tags = unlinkAllTagsOnImage(user_client, user_client.getPlates(id))
				break
			case "screen":
				tags = unlinkAllTagsOnImage(user_client, user_client.getScreens(id))
				break
		}
		
		println "Tags '"+tags+"' have been successfully unlinked from "+object_type+ " "+id
		
	}finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


def unlinkAllTagsOnImage(user_client, repository_wpr){
	// get the current tags
	List<TagAnnotationWrapper> tags = repository_wpr.getTags(user_client)

	tags.each{repository_wpr.unlink(user_client, it)}
	
	return tags
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*