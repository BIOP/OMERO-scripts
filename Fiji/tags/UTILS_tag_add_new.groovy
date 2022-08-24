#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type
#@String(label="New Tag", value = "new_tag") tagName


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the image to be imported (must be stored in a local environnement) and the ID of the dataset where to import the image.
 * 
 * == INPUTS ==
 *  - host
 *   -port
 *  - credentials 
 *  - id
 *  - object type
 *  - boolean to process all images in the current dataset
 *  - csv file to read
 *  - action to perform
 * 
 * == OUTPUTS ==
 *  - if adding a tag, it will upolad to OMERO every tags contained in the csv file and link them to the object specified by its id
 *  - if getting a tag, it will list all the tags linked to the current user AND ALL available tags in its group
 *  - if deleting a tag, it will delete ALL the tags linked to the object specified by its id
 * 
 * = DEPENDENCIES =
 *  - omero_ij : https://github.com/ome/omero-insight/releases/download/v5.7.0/omero_ij-5.7.0-all.jar
 *  - simple-omero-client : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 18.05.2022
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
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
 */


/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		
		switch (object_type){
		case "image":	
			processTag( user_client, user_client.getImage(id) )
			break	
		case "dataset":
			processTag( user_client, user_client.getDataset(id) )
			break
		case "project":
			processTag( user_client, user_client.getProject(id) )
			break
		case "well":
			processTag( user_client, user_client.getWells(id) )
			break
		case "plate":
			processTag( user_client, user_client.getPlates(id))
			break
		case "screen":
			processTag( user_client, user_client.getScreens(id))
			break
		}
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Adding the tag "+tagName+" for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Add a new tags to the object.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def processTag(user_client, wpr){
	// find if the requested tag already exists
	new_tag = user_client.getTags().find{ it.getName().equals(tagName) } ?: new TagAnnotationWrapper(new TagAnnotationData(tagName))
	
	// add the tag to the image if it is not already the case
	wpr.getTags(user_client).find{ it.getName().equals(new_tag.getName()) } ?: wpr.addTag(user_client, new_tag)
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*
import omero.gateway.model.DatasetData;
import omero.gateway.model.TagAnnotationData;
import omero.model.NamedValue
import ij.*
import ij.plugin.*
import ij.gui.PointRoi
import java.io.*;