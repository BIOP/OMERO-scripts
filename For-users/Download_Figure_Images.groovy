#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD


/* == CODE DESCRIPTION ==
 * This script downloads images from the selected figure(s)
 * 
 * The script automatically generates a CSV report that summarizes which image has been uploaded, with which tags.
 * The CSV report is saved in your Downloads folder.
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - Saving folder. 
 *  - Figure to download images from
 *  
 *  
 * == OUTPUTS ==	
 *  - image on OMERO
 *  - Tags on OMERO
 *  - CSV report in the Download folder
 * 
 * 
 * = DEPENDENCIES =
 *  - omero-ij-5.8.6-all
 *  - simple-omero-client-5.19.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2025.06-27
 * version : v1.0.0
 * 
 */


hasFailed = false
hasSilentlyFailed = false
endedByUser = false
message = ""
tokenSeparator = " | "
csvSeparator = ","


RAW_FOL = "Raw folder"
FIG_NAME = "Figure Name"
FIG_ID = "Figure ID"
IMG_IDS_OK = "Image IDs OK"
IMG_IDS_FAIL = "Image IDs Failed"
STS = "Status"


String DEFAULT_PATH_KEY = "scriptDefaultDir"
String FOL_PATH = "path";
String FIG = "figure"


// Connection to server
port = 4064
Client user_client = new Client()


try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	def message = "Cannot connect to "+host+". Please check your credentials"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}


if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()	
	Map<String, String> commonSummaryMap = new HashMap<>()
	def countImg = 0;
	
	try{		
		// get the userID
		def userId = user_client.getId()
		
		// get user's figures
		def figureNameIdMap
		def figureIdSizeMap
		(figureNameIdMap, figureIdSizeMap) = listOmeroFigures(user_client, userId)
		
		// generate the dialog box
		def figuresNames = (String [])((figureNameIdMap.keySet() as List).sort())
		def dialog = new Dialog(figuresNames)
		
		while(!dialog.getEnterPressed()){
	   		// Wait an answer from the user (Ok or Cancel)
		}
		
		if(dialog.getValidated()){		
			for(Map<String, String> selectedMap : dialog.getSelectedList()){
				Map<String, String> imgSummaryMap = new HashMap<>()
				
				// collect the user inputs
				String inputValues = selectedMap.collect{key, value -> ""+key+":"+value}.join("\n")
				IJLoggerInfo("LOCAL", "User input\n " + inputValues);
			
				String savingFolder = selectedMap.get(FOL_PATH)
				String existingFigureName = selectedMap.get(FIG)
				
				// get file id
				def fileId = figureNameIdMap.get(existingFigureName)

				// get file size
				def fileSize = figureIdSizeMap.get(fileId)
				
				// save original figure file
				def savingFigureFolder = new File(savingFolder, existingFigureName)
				if(!savingFigureFolder.exists())
					savingFigureFolder.mkdir()
				
				imgSummaryMap.put(RAW_FOL, savingFolder)
				
				File figureFile = null
				try{
					IJLoggerInfo("OMERO", "Downloading figure file '"+existingFigureName+"' : "+fileId+"...")
					figureFile = getAndSaveFigureFile(user_client, fileId, fileSize, existingFigureName, savingFigureFolder)
				}catch (Exception e){
					hasSilentlyFailed = true
					message = "The figure '"+existingFigureName+"' cannot be downloaded from OMERO."
					IJLoggerError("OMERO", message, e)
					imgSummaryMap.put(STS, "Failed")
					transferSummary.add(imgSummaryMap)
					continue
				}
				
				
				imgSummaryMap.put(FIG_NAME, existingFigureName)
				imgSummaryMap.put(FIG_ID, fileId)
				
				// list images within the figure
				def imageIds = []
				try{
					IJLoggerInfo("LOCAL", "Reading figure file '"+existingFigureName+"'.json and extract image IDs...")
					imageIds = listImageIdsFromFigureFile(figureFile)
				}catch (Exception e){
					hasSilentlyFailed = true
					message = "The figure file '"+existingFigureName+"' cannot be read properly and no image IDs can be extracted."
					IJLoggerError("LOCAL", message, e)
					imgSummaryMap.put(STS, "Failed")
					transferSummary.add(imgSummaryMap)
					continue
				}
				
				// download images on the computer
				def list_ids_ok = []
				def list_ids_failed = []
				for(def imageId : imageIds){
					try{
						IJLoggerInfo("OMERO", "Downloading image "+imageId+"...")
						downloadImage(user_client, imageId, savingFigureFolder)
						list_ids_ok.add(imageId)
					}catch (Exception e){
						hasSilentlyFailed = true
						message = "The image '"+imageId+"' cannot be downloaded."
						IJLoggerError("OMERO", message, e)
						imgSummaryMap.put(STS, "Failed")
						list_ids_failed.add(imageId)
					}
				}
				
				imgSummaryMap.put(IMG_IDS_FAIL, list_ids_failed.join(tokenSeparator))
				imgSummaryMap.put(IMG_IDS_OK, list_ids_ok.join(tokenSeparator))
				if(!hasSilentlyFailed)
					imgSummaryMap.put(STS, "OK")
				transferSummary.add(imgSummaryMap)
			}
		}else{
			IJLoggerInfo("LOCAL", "The script was ended by the user")
			endedByUser = true
		}
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
		if(!hasFailed){
			hasFailed = true
			message = "An error has occurred. Please look at the logs and the report to know where the processing has failed. "
		}
	}finally{
		// generate CSV report
		try{
			if(!endedByUser){
				IJLoggerInfo("CSV report", "Generate the CSV report...")
				generateCSVReport(transferSummary)
			}
		}catch(Exception e2){
			IJLoggerError(e2.toString(), "\n"+getErrorStackTraceAsString(e2))
			hasFailed = true
			message += "An error has occurred during csv report generation."
		}finally{
			// disconnect
			user_client.disconnect()
			IJLoggerInfo("OMERO","Disconnected from "+host)
			
			// print final popup
			if(!endedByUser){
				if(!hasFailed) {
					message += " A CSV report has been created in your 'Downloads' folder."
					if(hasSilentlyFailed){
						JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.WARNING_MESSAGE);
					}else{
						JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.INFORMATION_MESSAGE);
					}
				}else{
					JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}
}else{
	message = "Not able to connect to "+host
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
}
return


/**
 * Download the JSON file of the figure
 */
def getAndSaveFigureFile(user_client, fileId, fileSize, figureName, tmpLocation){
	def figureFile = new File(tmpLocation, figureName+".json")
	def errorOccured = false
	
	try (FileOutputStream stream = new FileOutputStream(figureFile)) {
		final int inc = 262144;
        RawFileStorePrx store = null;
        
        try{
	        store = user_client.getGateway().getRawFileService(user_client.getCtx());
	        store.setFileId(fileId);
	
	        long size = fileSize
	        long offset;
	        for (offset = 0; offset + inc < size; offset += inc) {
	            stream.write(store.read(offset, inc));
	        }
	        stream.write(store.read(offset, (int) (size - offset)));
        }catch(Exception e){
        	errorOccured = true
        }finally{
        	if(store != null){
        		store.close()
        	}
        	if(errorOccured){
        		throw e
        	}
        }		
	}catch(Exception e1){
		throw e1
	}
	return figureFile
}


/**
 * Query the list of figures from OMERO and get their id, name and size
 */
def listOmeroFigures(user_client, userId){
	def query = "SELECT a.file.id, a.file.name, a.file.size from FileAnnotation a WHERE a.ns='omero.web.figure.json'"
	def results = user_client.getGateway().getQueryService(user_client.getCtx()).projection(query, null)

	def nameIdMap = [:]
	def idSizeMap = [:]
	results.each{result->
		nameIdMap.put(result.get(1).getValue(), result.get(0).getValue())
		idSizeMap.put(result.get(0).getValue(), result.get(2).getValue())
	}

	return [nameIdMap, idSizeMap]
}


/**
 * Parse the JSON file of the figure and 
 * extract the IDs of the images used in that figure
 */
def listImageIdsFromFigureFile(figureFile){
    def imageIdList = []
    
	if(figureFile.exists()){
	    def figureFileContent = figureFile.getText(StandardCharsets.UTF_8.toString())
	    JSONObject obj = new JSONObject(figureFileContent);

	    obj.getJSONArray("panels").each {
	        if(it.keySet().contains("imageId")) {
	            imageIdList.add(it.get("imageId")) 
	        } 
	    }
		 
	}
	return imageIdList.unique()
}


/**
 * Download an image from OMERO, in the specified folder
 */
def downloadImage(user_client, id, dir){
	if(dir.exists()){
        user_client.getGateway().getFacility(TransferFacility.class).downloadImage(user_client.getCtx(), dir.getAbsolutePath(), id);
	}
    else {
        IJLoggerError("OMERO", "The following path does not exists : "+path+ ". Cannot download the image")
        throw new IOException()
    } 	
}


/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){

	def headerList = [RAW_FOL, FIG_NAME, FIG_ID, IMG_IDS_OK, IMG_IDS_FAIL, STS]
	String header = headerList.join(csvSeparator)
	String statusOverallSummary = ""
	
	// get all summaries
	transferSummaryList.each{imgSummaryMap -> 
		def statusSummaryList = []
		//loop over the parameters
		headerList.each{outputParam->
			if(imgSummaryMap.containsKey(outputParam))
				statusSummaryList.add(String.valueOf(imgSummaryMap.get(outputParam)))
			else
				statusSummaryList.add("-")
		}
		
		statusOverallSummary += statusSummaryList.join(csvSeparator) + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_Download_figure_images_report"
	String path = System.getProperty("user.home") + File.separator +"Downloads"
	IJLoggerInfo("CSV report", "Saving the report as '"+name+".csv' in "+path+"....")
	writeCSVFile(path, name, content)	
	IJLoggerInfo("CSV report", "DONE!")
}


/**
 * Save a csv file in the given path, with the given name
 */
def writeCSVFile(path, name, fileContent){
	// create the file locally
    File file = new File(path.toString() + File.separator + name + ".csv");

    try (BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
        buffer.write(fileContent);
	}catch(Exception e){
		throw e
	}
}


/**
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        "+message); 
}
def IJLoggerError(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        ["+title+"] -- "+message); 
}
def IJLoggerError(String title, String message, Exception e){
    IJLoggerError(title, message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerError(String message, Exception e){
    IJLoggerError(message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerWarn(String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    ["+title+"] -- "+message); 
}
def IJLoggerInfo(String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             "+message); 
}
def IJLoggerInfo(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             ["+title+"] -- "+message); 
}
def getCurrentDateAndHour(){
    LocalDateTime localDateTime = LocalDateTime.now();
    LocalTime localTime = localDateTime.toLocalTime();
    LocalDate localDate = localDateTime.toLocalDate();
    return ""+localDate.getYear()+
            (localDate.getMonthValue() < 10 ? "0"+localDate.getMonthValue():localDate.getMonthValue()) +
            (localDate.getDayOfMonth() < 10 ? "0"+localDate.getDayOfMonth():localDate.getDayOfMonth())+"-"+
            (localTime.getHour() < 10 ? "0"+localTime.getHour():localTime.getHour())+"h"+
            (localTime.getMinute() < 10 ? "0"+localTime.getMinute():localTime.getMinute())+"m"+
            (localTime.getSecond() < 10 ? "0"+localTime.getSecond():localTime.getSecond());
}


/**
 * 
 * Create the Dialog asking for the figures
 * 
 * */
public class Dialog extends JFrame {
	
	private JComboBox<String> cmbFigure;
    private JButton bnOk = new JButton("Finish");
    private JButton bnCancel = new JButton("Cancel");
    private JButton bnNext = new JButton("Next");
    private DefaultComboBoxModel<String> modelCmbFigure;

    	
	boolean enterPressed;
	boolean validated;
	
	String DEFAULT_PATH_KEY = "scriptDefaultDir"
	String FOL_PATH = "path";
	String FIG = "figure"
    
	File currentDir = IJ.getProperty(DEFAULT_PATH_KEY) == null ? new File("") : ((File)IJ.getProperty(DEFAULT_PATH_KEY))
	List<Map<String, String>> selectionList = new ArrayList<>()
	
	public Dialog(figure_names){		
		myDialog(figure_names)
	}
	
	// getters
	public boolean getEnterPressed(){return this.enterPressed}
	public boolean getValidated(){return this.validated}
	public List<Map<String, String>> getSelectedList(){return this.selectionList}
	
	// generate the dialog box
	public void myDialog(figure_names) {
		// set general frame
		this.setTitle("Select your import location on OMERO")
	    this.setVisible(true);
	    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	   // this.setPreferredSize(new Dimension(400, 250));
	    
	    // get the screen size
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double width = screenSize.getWidth();
        double height = screenSize.getHeight();
        
        // set location in the middle of the screen
	    this.setLocation((int)((width - 400)/2), (int)((height - 250)/2));
		
		// build figure combo model
		modelCmbFigure = new DefaultComboBoxModel<>(figure_names);
        cmbFigure = new JComboBox<>(modelCmbFigure);
		
        
        // Root folder for figure
        JLabel labRootFolder  = new JLabel("Saving folder");
        JTextField tfRootFolder = new JTextField();
        tfRootFolder.setColumns(15);
        tfRootFolder.setText(currentDir.getAbsolutePath())

        // button to choose root folder
        JButton bRootFolder = new JButton("Choose folder");
        bRootFolder.addActionListener(e->{
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setCurrentDirectory(currentDir);
            directoryChooser.setDialogTitle("Choose the saving folder");
            directoryChooser.showDialog(new JDialog(),"Select");

            if (directoryChooser.getSelectedFiles() != null){
                 tfRootFolder.setText(directoryChooser.getSelectedFile().getAbsolutePath());
                currentDir = directoryChooser.getSelectedFile()
                IJ.setProperty(DEFAULT_PATH_KEY, currentDir)
            }
        });
            
		
        // build Combo figure
        JPanel boxComboFigure = new JPanel();
        JLabel figureLabel = new JLabel("Figure");
        boxComboFigure.add(figureLabel);
        boxComboFigure.add(cmbFigure);
        boxComboFigure.setLayout(new FlowLayout());

              
        // build buttons
        JPanel boxButton = new JPanel();
        boxButton.add(bnNext);
        boxButton.add(bnOk);
        boxButton.add(bnCancel);
        boxButton.setLayout(new FlowLayout());
        
         // Folder box
        JPanel windowFolder = new JPanel();
        windowFolder.setLayout(new BoxLayout(windowFolder, BoxLayout.X_AXIS));
        windowFolder.add(labRootFolder);
        windowFolder.add(tfRootFolder);
        windowFolder.add(bRootFolder);
        
        
        // general panel
        JPanel windowNLGeneral = new JPanel();
        windowNLGeneral.setLayout(new BoxLayout(windowNLGeneral, BoxLayout.Y_AXIS));
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowFolder);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxComboFigure);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(boxButton);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        
        JPanel nicerWindow = new JPanel();
        nicerWindow.setLayout(new BoxLayout(nicerWindow, BoxLayout.X_AXIS));
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        nicerWindow.add(windowNLGeneral);
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        
		
		// add listener on Ok and Cancel button
		bnOk.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				
    				def rootFolder = (String)tfRootFolder.getText()
    				if(rootFolder != null && !rootFolder.isEmpty()){
						
						if(!checkInputs(tfRootFolder))
							return
		
						Map<String, String> selection = new HashMap<>()
						selection.put(FOL_PATH, rootFolder)
						selection.put(FIG, (String) cmbFigure.getSelectedItem())
						selectionList.add(selection)
    				}

    				enterPressed = true
    				validated = true;
    				
    				this.dispose()
    			}
			}
		)
		
		bnCancel.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				enterPressed = true
    				validated = false;
    				this.dispose()
    			}
			}
		)
		
		bnNext.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				if(!checkInputs(tfRootFolder))
    					return
    				
    				Map<String, String> selection = new HashMap<>()
    				selection.put(FOL_PATH, (String) tfRootFolder.getText())
    				selection.put(FIG, (String) cmbFigure.getSelectedItem())
					selectionList.add(selection)
										
					tfRootFolder.setText("");
    			}
			}
		)
		
		 // set main interface parameters
		this.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {

            }

            @Override
            public void windowClosed(WindowEvent e) {
				enterPressed = true
    			validated = false;
            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        });

        this.setContentPane(nicerWindow);
        this.pack();
    }
    
    private boolean checkInputs(tfRootFolder){
    	if(tfRootFolder.getText() == null || tfRootFolder.getText().isEmpty() || !(new File(tfRootFolder.getText())).exists()){
    		def message = "You must enter a valid folder to process"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
    	}
		return true			
    }
} 


/*
 * imports
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.FileAnnotationData
import omero.gateway.facility.TransferFacility
import omero.api.RawFileStorePrx;

import ij.IJ;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.json.*;
import java.nio.charset.StandardCharsets;

import javax.swing.JOptionPane;
import javax.swing.JFrame;
import java.awt.AWTEvent;
import java.util.stream.Collectors
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.*;
import java.awt.FlowLayout;
import javax.swing.BoxLayout
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.Dimension;
import java.awt.Toolkit;