#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id

// Connection to server
host = "omero-poc.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host

	try{
		processImage(user_client, user_client.getImage(id))
		println "Processing of image, id "+id+": DONE !"
	} finally {
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
} else {
	println "Not able to connect to "+host
}


def processImage(user_client, image_wpr){
	println image_wpr.getName()

  	def txtroi1 = new TextRoi( 0 , 0 ,50.0 , 50.0,"myTextArial" ,  new FontUtil().getFont("Arial", 2 , 72 as float ) )
	
	def roiArray = [txtroi1]
	def roisToUpload = ROIWrapper.fromImageJ(roiArray)
	/*// define a new Text ROI (sinple-omero-client ROI)
	def text = new TextWrapper(new TextData("myText", 0, 0))
	// get the shape settings
	def settings = text.asDataObject().getShapeSettings()
	// set the font size
	settings.setFontSize(new LengthI(72, UnitsLength.MILLIMETER))
	// create a list of ROI to upload
	def roisToUpload = []
	roisToUpload.add(text)
	
	// convert to a list of ROIWrapper
	def roisToUpload2 = []
	roisToUpload2.add(new ROIWrapper(roisToUpload))
*/

	// upload to OMERO
	image_wpr.saveROIs(user_client, roisToUpload)
}



/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import omero.gateway.model.*
import omero.model.*
import omero.model.enums.*
import ij.gui.TextRoi
import ij.util.FontUtil
