#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id

#@RoiManager rm


/* = CODE DESCRIPTION =
 * - This is a template to interact with OMERO. 
 * - The user enter the image ID and 
 * - The code reads the roi manager and save roi to OMERO in a nested way
 * 
 * == INPUTS ==
 *  - credentials 
 *  - image id
 * 
 * == OUTPUTS ==
 *  - rois on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.12.3 : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 31.08.2022
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
 * 
 * == HISTORY ==
 * - 2023-06-19 : Limit the number of server calls + update simple-omero-client to 5.12.3
 */

/**
 * Main. 
 * Connect to OMERO, 
 * read the roi manager
 * find a hierarchy between rois
 * import rois to OMERO keeping the hierarchy
 * disconnect from OMERO
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
		processRois(user_client, user_client.getImage(id))
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Import nested ROIs on image, id "+id+": DONE !\n"
	
}else{
	println "Not able to connect to "+host
}

/**
 * Get rois from the roi manager and find a hierarchy between rois (i.e. one roi contains inside another for example)
 * Save rois to OMERO in a nested way , each nest containing the main roi and the child.
 */
def processRois(user_client, image_wpr){
	def reducedRois = new HashMap()
	def allRois = new HashMap()
	def counter = 0
	
	// get rois from the roi manager
	def imageJListOfRois = rm.getRoisAsArray() as List
	
	// create rois map
	imageJListOfRois.each{allRois.put(it,false)}
	
	for(int i = 0; i<imageJListOfRois.size(); i++){
		def hasfoundNested = false
		for(int j = i; j<imageJListOfRois.size(); j++){
			if(allRois.get(imageJListOfRois.get(i)) == false || allRois.get(imageJListOfRois.get(j)) == false){
				def rectangleBound_1 = imageJListOfRois.get(i).getBounds()
				def rectangleBound_2 = imageJListOfRois.get(j).getBounds()
				
				def minx_1 = rectangleBound_1.x
				def maxx_1 = rectangleBound_1.x + rectangleBound_1.width
				def miny_1 = rectangleBound_1.y
				def maxy_1 = rectangleBound_1.y + rectangleBound_1.height
				
				def minx_2 = rectangleBound_2.x
				def maxx_2 = rectangleBound_2.x + rectangleBound_2.width
				def miny_2 = rectangleBound_2.y
				def maxy_2 = rectangleBound_2.y + rectangleBound_2.height
				
				// check if rois are nested or not
				if(((minx_1 >= minx_2 && maxx_1 <= maxx_2 && miny_1 >= miny_2 && maxy_1 <= maxy_2) ||
				   (minx_2 >= minx_1 && maxx_2 <= maxx_1 && miny_2 >= miny_1 && maxy_2 <= maxy_1)) && i!=j ) {
				   		// if nested, group the rois together
				   		hasfoundNested = true
					   	def listOfRois = new ArrayList()
						listOfRois.add(ROIWrapper.fromImageJ(Collections.singletonList(imageJListOfRois.get(i))).get(0))
						listOfRois.add(ROIWrapper.fromImageJ(Collections.singletonList(imageJListOfRois.get(j))).get(0))
						
						reducedRois.put(counter,listOfRois)
						allRois.replace(imageJListOfRois.get(i), false, true)
						allRois.replace(imageJListOfRois.get(j), false, true)
						counter++
				   }
			}
		}
		// if no nested, store rois one by one
		if(!hasfoundNested && allRois.get(imageJListOfRois.get(i)) == false){
			reducedRois.put(counter, ROIWrapper.fromImageJ(Collections.singletonList(imageJListOfRois.get(i))))
			allRois.replace(imageJListOfRois.get(i), false, true)
			counter++
		}
	}
	
	// save each grouped/single rois to OMERO 
	List<ROIWrapper> newRoisList = []
	reducedRois.values().each{roiWrapperList->
		def newRoi = new ROIWrapper()
		roiWrapperList.each{roiWrapper->
			def shapes = roiWrapper.asROIData().getShapes()
			def count = 0
			shapes.each{
				it.setText(""+(count++))
				newRoi.asROIData().addShapeData(it)
			}
		}
		newRoi.setName("CustomNested")
		newRoisList.add(newRoi)
	}
	image_wpr.saveROIs(user_client , newRoisList)
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import ij.*
import java.io.File