#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password' , persist=false) PASSWORD

#@RoiManager rm
#@ResultsTable rt

/* 

 */
 

// constants for GUI
String DEFAULT_PATH_KEY = "scriptDefaultDir2"
String PROCESS_PATH = "processPath";
String OMR_PRJ = "project";
String DST_NAMES = "datasets";
String IS_META = "isMeta";
String IS_SIZE = "isSizeDistribution";
String IS_STAT = "isStatistics";
String IS_ROI = "isROIs";
String IS_TURBIDITY = "isTurbidity";
String IS_DEL = "isDeleteAnnotations";
String DIR_SEPARATOR = ",";
String DATASET_SEPARATOR = "|";


// Connection to server
port = 4064
Client user_client = new Client()

try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	def message = "Cannot connect to "+host+". Please check your credentials"
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}


if (user_client.isConnected()){
	IJLoggerInfo("OMERO", "Connected to "+host)

	def endedByUser = false;

	try{		
		// get user's projects
		def projectWrapperList = user_client.getProjects()
		
		// get all the groups
		def availableGroups = user_client.getUser().getGroups().findAll(e->e.getId() != 0 && e.getId() != 1);
		
		// get the default group 
		def currentGroupID = user_client.getCurrentGroupId()
		
		// get project's name
		def projectNames = (String[])projectWrapperList.stream().map(ProjectWrapper::getName).collect(Collectors.toList()).sort()
		
		// generate the dialog box
		def dialog = new Dialog(projectWrapperList, projectNames)
	
		while(!dialog.getEnterPressed()){
	   		// Wait an answer from the user (Ok or Cancel)
		}
		
		//if ok
		if(dialog.getValidated()){
			
		}
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	}finally{
		// disconnect
		user_client.disconnect()
		IJLoggerInfo("OMERO","Disconnected from "+host)		
	}
}else{
	message = "Not able to connect to "+host
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
}

return



//logger
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
 * Create the Dialog asking for the project and dataset
 * 
 * */
public class Dialog extends JFrame {
	
	private JComboBox<String> cmbProject;
    private JButton bnOk = new JButton("Finish");
    private JButton bnCancel = new JButton("Cancel");
    private JButton bnNext = new JButton("Next");
    private DefaultComboBoxModel<String> modelCmbProject;
    private int nOptionMax = 6
    
    
	Client client;
	def userId;
	def project_list;
	boolean enterPressed;
	boolean validated;
	
	String DEFAULT_PATH_KEY = "scriptDefaultDir2"
	String PROCESS_PATH = "processPath";
    String OMR_PRJ = "project";
    String DST_NAMES = "datasets";
    String IS_META = "isMeta";
    String IS_SIZE = "isSizeDistribution";
    String IS_STAT = "isStatistics";
    String IS_ROI = "isROIs";
    String IS_TURBIDITY = "isTurbidity";
	String IS_DEL = "isDeleteAnnotations";
    String DIR_SEPARATOR = ",";
    String DATASET_SEPARATOR = "|";
    String SELECT_ALL = "Select all"
    String NO_SELECTION = "NO DATASET SELECTED"
	
	File currentDir = IJ.getProperty(DEFAULT_PATH_KEY) == null ? new File("") : ((File)IJ.getProperty(DEFAULT_PATH_KEY))
	List<Map<String, String>> selectionList = new ArrayList<>()
	Map<String, List<String>> projectNewDatasets = new HashMap<>()
	
	public Dialog(project_list, project_names){
		this.project_list = project_list
		
		project_names.each{
			projectNewDatasets.put(it, new ArrayList<>())
		}
		
    	def project = project_list.find{it.getName() == project_names[0]}
    	def dataset_list = project.getDatasets()
		def dataset_names = dataset_list.stream().map(DatasetWrapper::getName).collect(Collectors.toList())
		projectNewDatasets.put(project_names[0], dataset_names)
		
		myDialog(project_names)
	}
	
	// getters
	public boolean getEnterPressed(){return this.enterPressed}
	public boolean getValidated(){return this.validated}
	public List<Map<String, String>> getSelectedList(){return this.selectionList}
	
	// generate the dialog box
	public void myDialog(project_names) {
		// set general frame
		this.setTitle("Select your import options on OMERO")
	    this.setVisible(true);
	    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	   // this.setPreferredSize(new Dimension(400, 250));
	    
	    // get the screen size
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double width = screenSize.getWidth();
        double height = screenSize.getHeight();
        
        // set location in the middle of the screen
	    this.setLocation((int)((width - 400)/2), (int)((height - 250)/2));
		
		// build project combo model
		modelCmbProject = new DefaultComboBoxModel<>(project_names);
        cmbProject = new JComboBox<>(modelCmbProject);
        
        JButton dropDownButton = new JButton("Select");

    	// display the list of available datasets
		JPanel checkboxPanel = new JPanel()
		checkboxPanel.layout = new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS)
		
	    JCheckBox selectAllBox = new JCheckBox(SELECT_ALL)
    	checkboxPanel.add(selectAllBox)

        JPopupMenu popupMenu = new JPopupMenu();
        def datasetNames = projectNewDatasets.get(project_names[0]).sort()
        JCheckBox[] checkBoxes = new JCheckBox[datasetNames.size()]
        datasetNames.eachWithIndex{name, idx ->
        	checkBoxes[idx] = new JCheckBox(name)
        }
        
		JTextField tfSelectedDatasets = new JTextField();
		tfSelectedDatasets.setText(NO_SELECTION)
		tfSelectedDatasets.setColumns(15);
                                
        for (JCheckBox checkBox : checkBoxes) {
            checkboxPanel.add(checkBox);
            checkBox.addActionListener(e -> {
                List<String> selected = new ArrayList<>();
                for (JCheckBox cb : checkBoxes) {
                    if (cb.isSelected() && !cb.getText().equals(SELECT_ALL)) {
                        selected.add(cb.getText());
                    }
                }
                tfSelectedDatasets.setText(selected.join(DATASET_SEPARATOR))
            });
        }
        
	    selectAllBox.addActionListener {
	        def isSelected = selectAllBox.isSelected();
	        List<String> selected = new ArrayList<>();
	        checkboxPanel.getComponents().each { 
	        	JCheckBox cb = ((JCheckBox)it)
	        	cb.setSelected(isSelected) 
	        	if (cb.isSelected() && !cb.getText().equals(SELECT_ALL)) {
                    selected.add(cb.getText());
                }
	        }
	        tfSelectedDatasets.setText(selected.join(DATASET_SEPARATOR))
	    }
       
		def scrollPane = new JScrollPane(checkboxPanel)
		scrollPane.setPreferredSize(new Dimension(200, (int)(checkBoxes[0].preferredSize.height * nOptionMax)))
		popupMenu.add(scrollPane)
		
		dropDownButton.addActionListener(e -> {
            popupMenu.show(dropDownButton, 0, dropDownButton.getHeight());
        });


        JLabel labProcessFolder  = new JLabel("Processed Data dir");
        JTextField tfProcessFolder = new JTextField();
        tfProcessFolder.setColumns(15);
        tfProcessFolder.setText(currentDir.getAbsolutePath())
       

        // button to choose root folder
        JButton bProcessFolder = new JButton("Choose folder");
        bProcessFolder.addActionListener(e->{
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setCurrentDirectory(currentDir);
            directoryChooser.setDialogTitle("Choose the Processed Data folder");
            directoryChooser.showDialog(new JDialog(),"Select");

            if (directoryChooser.getSelectedFiles() != null){
                tfProcessFolder.setText(directoryChooser.getSelectedFile().getAbsolutePath());
                currentDir = directoryChooser.getSelectedFile()
                IJ.setProperty(DEFAULT_PATH_KEY, currentDir)
            }
        });
        
        // checkbox to import Acquisition metadata
        JCheckBox chkMetdata = new JCheckBox("Acquisition metadata");
        chkMetdata.setSelected(true);
        
        // checkbox to import Particle size distribution
        JCheckBox chkSize = new JCheckBox("Particle size distribution");
        chkSize.setSelected(true);
        
        // checkbox to import Particle statistics
        JCheckBox chkStats = new JCheckBox("Particle statistics");
        chkStats.setSelected(true);

        // checkbox to import Particle ROIs
        JCheckBox chkRois = new JCheckBox("Particle ROIs");
        chkRois.setSelected(true);
        
        // checkbox to import Particle ROIs
        JCheckBox chkTurbidity = new JCheckBox("Turbidity metrics");
        chkTurbidity.setSelected(true);
        
        // checkbox to import Acquisition metadata
        JCheckBox chkDeldata = new JCheckBox("Delete previous annotations");
        chkDeldata.setSelected(false);
        
        // build Combo project
        JPanel boxComboProject = new JPanel();
        JLabel projectLabel = new JLabel("Project");
        boxComboProject.add(projectLabel);
        boxComboProject.add(cmbProject);
        boxComboProject.setLayout(new FlowLayout());
        
        // build Combo dataset
        JPanel boxComboDataset = new JPanel();
        JLabel datasetLabel = new JLabel("Dataset(s)");
        boxComboDataset.add(datasetLabel);
        boxComboDataset.add(dropDownButton);
        boxComboDataset.add(tfSelectedDatasets);
        boxComboDataset.setLayout(new FlowLayout());
        
        // build buttons
        JPanel boxButton = new JPanel();
        boxButton.add(bnNext);
        boxButton.add(bnOk);
        boxButton.add(bnCancel);
        boxButton.setLayout(new FlowLayout());

         // Folder box
        JPanel windowProcessFolder = new JPanel();
        windowProcessFolder.setLayout(new BoxLayout(windowProcessFolder, BoxLayout.X_AXIS));
        windowProcessFolder.add(labProcessFolder);
        windowProcessFolder.add(tfProcessFolder);
        windowProcessFolder.add(bProcessFolder);
        
        // general panel
        JPanel windowNLGeneral = new JPanel();
        windowNLGeneral.setLayout(new BoxLayout(windowNLGeneral, BoxLayout.Y_AXIS));
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowProcessFolder);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxComboProject);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxComboDataset);
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkMetdata);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkSize);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkStats);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkRois);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkTurbidity);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkDeldata);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxButton);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        
        JPanel nicerWindow = new JPanel();
        nicerWindow.setLayout(new BoxLayout(nicerWindow, BoxLayout.X_AXIS));
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        nicerWindow.add(windowNLGeneral);
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        
                
        // add listener on project combo box
        cmbProject.addItemListener(
			new ItemListener(){
			    @Override
			    public void itemStateChanged(ItemEvent e) {
					// get the datasets corresponding to the selected project
			        def chosen_project = (String) cmbProject.getSelectedItem()
			        def dataset_names
			        if(projectNewDatasets.get(chosen_project).isEmpty()){
			        	def project = project_list.find{it.getName() == chosen_project}
						def dataset_list = project.getDatasets()
						dataset_names = dataset_list.stream().map(DatasetWrapper::getName).collect(Collectors.toList()).sort()
						projectNewDatasets.put(chosen_project, dataset_names)
			        }else{
			        	dataset_names = projectNewDatasets.get(chosen_project)
			        }
			        
			        // update the dataset combo box
					checkboxPanel.removeAll();
					checkboxPanel.add(selectAllBox)
					
					checkBoxes = new JCheckBox[dataset_names.size()]
				    dataset_names.eachWithIndex{name, idx ->
				    	checkBoxes[idx] = new JCheckBox(name)
				    }
				    tfSelectedDatasets.setText(NO_SELECTION)

        			for (JCheckBox checkbox : checkBoxes) {
        				checkboxPanel.add(checkbox);
        				checkbox.addActionListener(a -> {
			                List<String> selected = new ArrayList<>();
			                for (JCheckBox cb : checkBoxes) {
			                    if (cb.isSelected()) {
			                        selected.add(cb.getText());
			                    }
			                }
			                tfSelectedDatasets.setText(selected.join(DATASET_SEPARATOR))
			            });
        			}
			    }
			}
		);
		
		// add listener on Ok and Cancel button
		bnOk.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				
    				def rootFolder = (String)tfProcessFolder.getText()
    				if(rootFolder != null && !rootFolder.isEmpty()){
						
						if(!checkInputs(checkboxPanel, tfProcessFolder))
							return
		
						Map<String, String> selection = new HashMap<>()
						selection.put(PROCESS_PATH, (String) tfProcessFolder.getText())
						selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
						
						def datasets = []
						checkboxPanel.getComponents().each { 
							def chk = ((JCheckBox)it)
							if(chk.isSelected() && !chk.getText().equals(SELECT_ALL)){
								datasets.add(chk.getText())
							}
						}
						selection.put(DST_NAMES, String.valueOf(datasets.join(DATASET_SEPARATOR)))
						
						selection.put(IS_META, String.valueOf(chkMetdata.isSelected()))
						selection.put(IS_SIZE, String.valueOf(chkSize.isSelected()))
						selection.put(IS_STAT, String.valueOf(chkStats.isSelected()))
						selection.put(IS_ROI, String.valueOf(chkRois.isSelected()))
						selection.put(IS_TURBIDITY, String.valueOf(chkTurbidity.isSelected()))
						selection.put(IS_DEL, String.valueOf(chkDeldata.isSelected()))
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
					if(!checkInputs(checkboxPanel, tfProcessFolder))
						return
		
					Map<String, String> selection = new HashMap<>()
					selection.put(PROCESS_PATH, (String) tfProcessFolder.getText())
					selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
					
					def datasets = []
					checkboxPanel.getComponents().each { 
						def chk = ((JCheckBox)it)
						if(chk.isSelected() && !chk.getText().equals(SELECT_ALL)){
							datasets.add(chk.getText())
						}
					}
					selection.put(DST_NAMES, String.valueOf(datasets.join(DATASET_SEPARATOR)))
					
					selection.put(IS_META, String.valueOf(chkMetdata.isSelected()))
					selection.put(IS_SIZE, String.valueOf(chkSize.isSelected()))
					selection.put(IS_STAT, String.valueOf(chkStats.isSelected()))
					selection.put(IS_ROI, String.valueOf(chkRois.isSelected()))
					selection.put(IS_TURBIDITY, String.valueOf(chkTurbidity.isSelected()))
					selection.put(IS_DEL, String.valueOf(chkDeldata.isSelected()))
					selectionList.add(selection)
					
					// reset UI
					checkboxPanel.getComponents().each { ((JCheckBox)it).setSelected(false) }				
					tfProcessFolder.setText("");
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
    
    private boolean checkInputs(checkboxPanel, tfProcessFolder){
    	if(tfProcessFolder.getText() == null || tfProcessFolder.getText().isEmpty() || !(new File(tfProcessFolder.getText())).exists()){
    		def message = "You must enter a valid folder for processed images"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
    	}
    	
    	def isDatasetSelected = false
    	checkboxPanel.getComponents().each { 
			def chk = ((JCheckBox)it)
			if(chk.isSelected()){
				isDatasetSelected = true
			}
		}
		
		if(!isDatasetSelected){
			def message = "You must select at least one dataset"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}

		return true			
    }
} 



// imports
import ij.IJ
import ij.WindowManager
import ij.Prefs
import ij.gui.Roi
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.roi.*
import omero.gateway.model.TagAnnotationData;
import omero.gateway.model.ProjectData
import omero.gateway.model.ROIData
import omero.gateway.model.DatasetData
import omero.gateway.model.ChannelData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import omero.model.NamedValue
import java.awt.Color;


import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.awt.Rectangle
import javax.swing.JOptionPane; 