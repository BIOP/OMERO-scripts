import qupath.ext.biop.servers.omero.raw.*
import qupath.lib.scripting.QP
import qupath.lib.gui.measure.ObservableMeasurementTableData;

/*
 * = DEPENDENCIES =
 *  - qupath-extension-biop-omero - latest version : https://github.com/BIOP/qupath-extension-biop-omero/releases
 *
 * = REQUIREMENTS =
 *  - A project must be open in QuPath
 *  - The connection to omero-server.epfl.ch needs to be established (with credentials) before running the script
 *  
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 20.10.2022
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
 * There is many implementations to send pathObjects to OMERO : 
 * 
 * /// sending an OMERO.table 
 * 		1. sendDetectionMeasurementTable(pathObjects, server, image_data) ==> send measurement table with only the specified pathObjects 
 * 		2. sendDetectionMeasurementTable(server, image_data) ==> send measurement table with all detections
 * 		
 * 	/// sending a csv file
 * 		1. sendDetectionMeasurementTableAsCSV(pathObjects, server, image_data) ==> send measurement table with only the specified pathObjects 
 * 		2. sendDetectionMeasurementTableAsCSV(server, image_data) ==> send measurement table with all detections
 * 
 */
 

/**
 * Send the detection measurement table to OMERO as an OMERO.table and a csv file, 
 * attached to the current opened image.
 * 
 **/

// get the current displayed image on QuPath
ImageServer<?> server = QP.getCurrentServer()

// check if the current server is an OMERO server. If not, throw an error
if(!(server instanceof OmeroRawImageServer)){
	Dialogs.showErrorMessage("Measurement table sending","Your image is not from OMERO ; please use an image that comes from OMERO to use this script");
	return
}

// get all detection objects
Collection<PathObject> pathObjects = QP.getDetectionObjects()

// get image data
def imageData = QP.getCurrentImageData()

// send the table to OMERO as OMERO.table
boolean wasSent = OmeroRawScripting.sendDetectionMeasurementTable(pathObjects, server, imageData);
if(wasSent)
	println "Detection table sent to OMERO as OMERO.table"
else
	println "An issue occurs when trying to send table to OMERO"


// send the table to OMERO as csv file
wasSent = OmeroRawScripting.sendDetectionMeasurementTableAsCSV(pathObjects, server, imageData);
if(wasSent)
	println "Detection table sent to OMERO as csv file"
else
	println "An issue occurs when trying to send csv file to OMERO"


