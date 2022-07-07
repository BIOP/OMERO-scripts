#@String(label="Host", value="omero-server.epfl.ch") host
#@Integer(label="Port", value = 4064) port
#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@File(label="Image path") imgPath
#@String(label="Import in", choices={"dataset","well"}) object_type
#@Long(label="Parent ID", value=119273) id
#@Boolean(label="Show images") showImages


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * User can specify the image to be imported (must be stored in a local environnement) and the ID of the dataset where to import the image.
 * 
 * == INPUTS ==
 *  - host
 *  - port
 *  - credentials 
 *  - image 
 *  - dataset id
 *  - display imported image or not
 * 
 * == OUTPUTS ==
 *  - Import the specified image on OMERO
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 *  - 
 * 
 * = DEPENDENCIES =
 *  - omero_ij : https://github.com/ome/omero-insight/releases/download/v5.7.0/omero_ij-5.7.0-all.jar
 *  - simple-omero-client : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet & Rémy Dornier, EPFL - SV -PTECH - BIOP 
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

// Only works with project/dataset/image => not with screen/plate/well


/**
 * Main. Connect to OMERO, import an image on OMERO and disconnect from OMERO
 * 
 */

// Connection to server
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host +"\n"
	
	try{
		getUserInformation(user_client)
		img_wpr = importImageOnOmero(user_client)
		
	} finally{
		user_client.disconnect()
		println "\n Disonnected "+host
	}
	
	println "Importation in OMERO of image "+imgPath.getName()+", in dataset "+user_client.getDataset(id).getName()+" (id "+id+") : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Display information about the logged-in user
 * 
 * input
 * 		user_client : OMERO client
 */
def getUserInformation(user_client){
	println "User ID : " + user_client.getId()
	ExperimenterWrapper ew = user_client.getUser()
	println "User name : " + ew.getFirstName() + " " + ew.getLastName()
	println "email : "  + ew.getEmail()
	println "Institution : " + ew.getInstitution()
	GroupWrapper[] gw = ew.getGroups().toArray()
	println "User groups : " 
	for (GroupWrapper gpw : gw)
		println "-  " + gpw.getName() + " ; Description : " +gpw.getDescription()
}


/**
 * Import an image on OMERO in the specified dataset/well
 * 
 * input
 * 		user_client : OMERO client
 */
def importImageOnOmero(user_client){
	// clear Fiji env
	IJ.run("Close All", "");
	
	// Upload Raw Image to OMERO and get newly created ID
	if(object_type == "dataset"){
		image_omeroID = user_client.getDataset(id).importImage(user_client, imgPath.toString())
		
		// print imported images
		image_omeroID.each{println "\n Image '"+  user_client.getImage(it).getName() +"' was uploaded to OMERO with \n - ID : "+ it +
		"\n - in dataset : " + user_client.getDataset(id).getName()+" (id : "+id+")"+
		"\n - in project : "+ user_client.getImage(it).getProjects(user_client).get(0).getName()+ " (id : "+ user_client.getImage(it).getProjects(user_client).get(0).getId() +")"}
	}
	else{
		image_omeroID = user_client.getWell(id).importImages(user_client, imgPath.toString())
		
		// print imported images
		image_omeroID.each{println "\n Image '"+  user_client.getImage(it).getName() +"' was uploaded to OMERO with \n - ID : "+ it +
		"\n - in well : " + user_client.getWell(id).getName()+" (id : "+id+")"+
		"\n - in plate : "+ user_client.getImage(it).getPlates(user_client).get(0).getName()+ " (id : "+ user_client.getImage(it).getPlates(user_client).get(0).getId() +
		"\n - in screen : "+ user_client.getImage(it).getScreens(user_client).get(0).getName()+ " (id : "+ user_client.getImage(it).getScreens(user_client).get(0).getId() +")"}
	}
	

	// Show the imported image
	if (showImages) IJ.run("Bio-Formats Importer", "open="+imgPath+" autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");

}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*
import ij.*
import ij.plugin.*
import ij.gui.PointRoi