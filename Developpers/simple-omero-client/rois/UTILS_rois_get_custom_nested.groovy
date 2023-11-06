#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id

#@RoiManager rm


/* = CODE DESCRIPTION =
 * - This is a template to interact with OMERO. 
 * - The user enter the image ID and 
 * - The code reads ROIs attached to the image on OMERO and show the image
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
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 23.09.2022
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
 * - 2023-06-16 : Remove unnecessary calls to OMERO server and imports
 */

/**
 * Main. 
 * Connect to OMERO, 
 * read ROIs o the specified image
 * show the image with ROIs
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
		println "ROIs importation in FIJI from the image, id "+id+": DONE !\n"
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}else{
	println "Not able to connect to "+host
}


/**
 * Read the ROIs from OMERO and treat nested ROIs as multiple independant ROIs.
 * Display the image with ROIs.
 */
def processRois(user_client, img_wpr){
	IJ.run("Close All", "");
	rm.reset()
	def roi_wpr_list = img_wpr.getROIs(user_client)
	def ij_roi_list = new ArrayList()
	
	roi_wpr_list.each{
		ij_roi_list.addAll(readNestedRoiWithoutXor(it))
	}
	
	ij_roi_list.each{
		rm.addRoi(it)
	}
	
	def imp = img_wpr.toImagePlus(user_client)
	imp.show()
	rm.runCommand(imp,"Show All");
}

/**
 * Code adapted from simple-omero-client @pierrePouchin
 * 
 * Read the ROIs from OMERO and treat nested ROIs as multiple independant ROIs.
 * 
 */
def readNestedRoiWithoutXor(roi_wpr){
	def shape_list = roi_wpr.getShapes()
	def ij_roi_list = new ArrayList()
	
	println "BE CAREFUL : each nested ROI are imported as multiple ROIs, not as a grouped one"
	def roiID = roi_wpr.getId()
	
	shape_list.each{
		def roi = it.toImageJ()
		String img_name = it.getText();
	
		 if (img_name.isEmpty()) {
             roi.setName(String.format("%d-%d", roiID, it.getId()));
         } else {
             roi.setName(img_name);
         }
         
		 roi.setStrokeColor(Color.white)
		 roi.setFillColor(null)
		 roi.setStrokeWidth(1)
		
         roi.setProperty("ROI_ID", String.valueOf(roiID));
         ij_roi_list.add(roi)
	}
	
	return ij_roi_list
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import ij.*
import java.awt.Color