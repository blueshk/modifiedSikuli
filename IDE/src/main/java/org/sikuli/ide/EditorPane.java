/*
 * Copyright 2010-2014, Sikuli.org, sikulix.com
 * Released under the MIT License.
 *
 * modified RaiMan 2014
 */
package org.sikuli.ide;

import org.sikuli.basics.PreferencesUser;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.nio.charset.Charset;
import java.util.Stack;
import javax.swing.plaf.basic.BasicComboPopup;

import org.sikuli.basics.Settings;
import org.sikuli.basics.Debug;
import org.sikuli.basics.FileManager;
import org.sikuli.idesupport.IIndentationLogic;
import org.sikuli.script.Location;
import org.sikuli.script.Image;
import org.sikuli.script.ImagePath;
import org.sikuli.script.Runner;
import org.sikuli.script.Sikulix;
import org.sikuli.scriptrunner.IScriptRunner;
import org.sikuli.scriptrunner.ScriptingSupport;
import org.sikuli.syntaxhighlight.ResolutionException;
import org.sikuli.syntaxhighlight.grammar.Lexer;
import org.sikuli.syntaxhighlight.grammar.Token;
import org.sikuli.syntaxhighlight.grammar.TokenType;

public class EditorPane extends JTextPane implements KeyListener, CaretListener {

	private static final String me = "EditorPane: ";
	private static final int lvl = 3;

	private static void log(int level, String message, Object... args) {
		Debug.logx(level, me + message, args);
	}

	private static TransferHandler transferHandler = null;
	private static final Map<String, Lexer> lexers = new HashMap<String, Lexer>();
	private PreferencesUser pref;

	private File scriptSource = null;
	private File scriptParent = null;
	private File scriptImages = null;
	private String scriptType = null;
	private boolean scriptIsTemp = false;

	private boolean scriptIsDirty = false;
	private DirtyHandler dirtyHandler;

	private File _editingFile;
//	private String scriptType = null;
	private String _srcBundlePath = null;
	private boolean _srcBundleTemp = false;
	private String sikuliContentType;

	private EditorCurrentLineHighlighter _highlighter = null;
	private EditorUndoManager _undo = null;
	// TODO: move to SikuliDocument ????
	private IIndentationLogic _indentationLogic = null;

	private boolean hasErrorHighlight = false;

	public boolean showThumbs;
	static Pattern patPngStr = Pattern.compile("(\"[^\"]+?\\.(?i)(png|jpg|jpeg)\")");
	static Pattern patCaptureBtn = Pattern.compile("(\"__CLICK-TO-CAPTURE__\")");
	static Pattern patPatternStr = Pattern.compile(
					"\\b(Pattern\\s*\\(\".*?\"\\)(\\.\\w+\\([^)]*\\))+)");
	static Pattern patRegionStr = Pattern.compile(
					"\\b(Region\\s*\\((-?[\\d\\s],?)+\\))");
	static Pattern patLocationStr = Pattern.compile(
					"\\b(Location\\s*\\((-?[\\d\\s],?)+\\))");

	//TODO what is it for???
	private int _caret_last_x = -1;
	private boolean _can_update_caret_last_x = true;
	private SikuliIDEPopUpMenu popMenuImage;
	private SikuliEditorKit editorKit;
	private EditorViewFactory editorViewFactory;
	private SikuliIDE sikuliIDE = null;
	private int caretPosition = -1;
        private BasicComboPopup outline=null;
        private JPopupMenu menu=new JPopupMenu();
        private String caseTmp="",suitTmp="",cstring="";
        private Stack<String> sb=new Stack<String>();
        private int count=0;
	//<editor-fold defaultstate="collapsed" desc="Initialization">
	public EditorPane(SikuliIDE ide) {
		pref = PreferencesUser.getInstance();
		showThumbs = !pref.getPrefMorePlainText();
		sikuliIDE = ide;
		log(lvl, "EditorPane: creating new pane (constructor)");
	}

	public void saveCaretPosition() {
		caretPosition = getCaretPosition();
	}

	public void restoreCaretPosition() {
		if (caretPosition < 0) {
			return;
		}
		if (caretPosition < getDocument().getLength()) {
			setCaretPosition(caretPosition);
		} else {
			setCaretPosition(getDocument().getLength() - 1);
		}
		caretPosition = -1;
	}

	public void initBeforeLoad(String scriptType) {
		initBeforeLoad(scriptType, false);
	}

	public void reInit(String scriptType) {
		initBeforeLoad(scriptType, true);
	}

	private void initBeforeLoad(String scriptType, boolean reInit) {
		String scrType = null;
		boolean paneIsEmpty = false;

		log(lvl, "initBeforeLoad: %s", scriptType);
		if (scriptType == null) {
			scriptType = Runner.EDEFAULT;
			paneIsEmpty = true;
		}

		if (Runner.EPYTHON.equals(scriptType)) {
			scrType = Runner.CPYTHON;
			_indentationLogic = SikuliIDE.getIDESupport(scriptType).getIndentationLogic();
			_indentationLogic.setTabWidth(pref.getTabWidth());
		} else if (Runner.ERUBY.equals(scriptType)) {
			scrType = Runner.CRUBY;
			_indentationLogic = null;
		}

//TODO should know, that scripttype not changed here to avoid unnecessary new setups
		if (scrType != null) {
			sikuliContentType = scrType;
			editorKit = new SikuliEditorKit();
			editorViewFactory = (EditorViewFactory) editorKit.getViewFactory();
			setEditorKit(editorKit);
			setContentType(scrType);

			if (_indentationLogic != null) {
				pref.addPreferenceChangeListener(new PreferenceChangeListener() {
					@Override
					public void preferenceChange(PreferenceChangeEvent event) {
						if (event.getKey().equals("TAB_WIDTH")) {
							_indentationLogic.setTabWidth(Integer.parseInt(event.getNewValue()));
						}
					}
				});
			}
		}

		if (transferHandler == null) {
			transferHandler = new MyTransferHandler();
		}
		setTransferHandler(transferHandler);

		if (_highlighter == null) {
			_highlighter = new EditorCurrentLineHighlighter(this);
			addCaretListener(_highlighter);
			initKeyMap();
			addKeyListener(this);
			addCaretListener(this);
		}

		setFont(new Font(pref.getFontName(), Font.PLAIN, pref.getFontSize()));
		setMargin(new Insets(3, 3, 3, 3));
		setBackground(Color.WHITE);
		if (!Settings.isMac()) {
			setSelectionColor(new Color(170, 200, 255));
		}

		updateDocumentListeners("initBeforeLoad");

		popMenuImage = new SikuliIDEPopUpMenu("POP_IMAGE", this);
		if (!popMenuImage.isValidMenu()) {
			popMenuImage = null;
		}

		if (paneIsEmpty || reInit) {
//			this.setText(String.format(Settings.TypeCommentDefault, getSikuliContentType()));
			this.setText("");
		}
		SikuliIDE.getStatusbar().setCurrentContentType(getSikuliContentType());
		log(lvl, "InitTab: (%s)", getSikuliContentType());
		if (!ScriptingSupport.hasTypeRunner(getSikuliContentType())) {
			Sikulix.popup("No installed runner supports (" + getSikuliContentType() + ")\n"
							+ "Trying to run the script will crash IDE!", "... serious problem detected!");
		}
                caseTmp=createCaseTemplate();
                suitTmp=createSuiteTemplate();
                 createPopupMenu();
                 
                 this.addMouseListener(new MyActionLister());
                 
	}
        
           public int getCurrentTabId(){
	   String modulename=SikuliIDE.getInstance().getCurrentFilename();
		modulename=modulename.substring(modulename.lastIndexOf("\\")+1);
		modulename=modulename.substring(0,modulename.lastIndexOf("."));
		int ret=SikuliIDE.getInstance().getCodePanes(modulename);
		return ret;
   }
           public void createPopupMenu(){
	  
	  menu=new JPopupMenu();
	  JMenuItem header=new JMenuItem("Add Suite Header");
	  header.addActionListener(new ActionListener(){

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			Document doc=getDocument();
			insertString(0,suitTmp);
		}
		  
	  });
	  
	  
	  JMenuItem newCase=new JMenuItem("Create New Case");
	  newCase.addActionListener(new ActionListener(){

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			String modulename=SikuliIDE.getInstance().getCurrentFilename();
                        if (modulename.indexOf("\\") !=-1){
                            modulename=modulename.substring(modulename.lastIndexOf("\\")+1);
                            modulename=modulename.substring(0,modulename.lastIndexOf("."));
                        }if (modulename.indexOf("/")!=-1){
                            modulename=modulename.substring(modulename.lastIndexOf("/")+1);
                            modulename=modulename.substring(0,modulename.lastIndexOf("."));
                        }
			String casename=JOptionPane.showInputDialog("Input CaseName","test_"+modulename+"_");
			if (casename==null)return;
			Document doc=getDocument();
			String template=caseTmp.replace("<name>", casename);
			insertString(doc.getLength(),template);
		
		}
		  
	  });
	  menu.add(header);
	  menu.addSeparator();
	  menu.add(newCase);
	  menu.addSeparator();
	  JMenuItem gtFunc=new JMenuItem("GoTo Function");

	  gtFunc.addActionListener(new ActionListener(){

		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			String sel=EditorPane.this.getSelectedText();
			try{
				if (sel==null || sel.equals(""))return;
				if (sel.contains(".")){// if moduleName.func..
					String tmp[]=sel.split("\\.");
					int ret=SikuliIDE.getInstance().getCodePanes(tmp[0]);
					if (ret==-1){// if not opened the code or self.func, then search in current doc
						SikuliIDE.getInstance().history.push("line:"+EditorPane.this.getLineAtCaret(getCaretPosition()));
						jumpTo(tmp[1]);
						}else{//select the tab and search the func
							SikuliIDE.getInstance().history.push("tab:"+getCurrentTabId());
							SikuliIDE.getInstance().autoSelectTab(ret);
							SikuliIDE.getInstance().getCurrentCodePane().jumpTo(tmp[1]);
							
							
							
							
						}

				}else{
					if (sel.contains(",")){// if for specified call:ForAll.callM(ModuleName,"funcName",params),selection: ModuleName,"funcName"
						String tmp[]=sel.split(",");
						int ret=SikuliIDE.getInstance().getCodePanes(tmp[0]);
						if (ret!=-1){
							tmp[1]=tmp[1].substring(1,tmp[1].length()-1);
							SikuliIDE.getInstance().history.push("tab:"+getCurrentTabId());
							SikuliIDE.getInstance().autoSelectTab(ret);
							SikuliIDE.getInstance().getCurrentCodePane().jumpTo(tmp[1]);

							
							
						}
					
					}else{//for normal call:funcname(),search in current doc
					
						SikuliIDE.getInstance().history.push("line:"+EditorPane.this.getLineAtCaret(getCaretPosition()));
						jumpTo(sel);
						
					}
			
			}
		}catch(Exception ex){}
		}
		  
	  });
	  menu.add(gtFunc);
	  JMenuItem gt=new JMenuItem("GoTo Line");
	  gt.addActionListener(new ActionListener(){

		@Override
		public void actionPerformed(ActionEvent e) {
			// TODO Auto-generated method stub
			String lineno=JOptionPane.showInputDialog("Input lineno","");
			if (lineno==null ||lineno.equals(""))return;
			try{
				SikuliIDE.getInstance().history.push("line:"+EditorPane.this.getLineAtCaret(getCaretPosition()));
				int i=Integer.parseInt(lineno);
				jumpTo(i);
				
			}catch(Exception ex){
				return;
			}
			
		}
  
	  });
	  menu.add(gt);
	  JMenuItem back=new JMenuItem("back");
	  back.addActionListener(new ActionListener(){

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			if (SikuliIDE.getInstance().history.size()>0){
				String i=SikuliIDE.getInstance().history.pop();
				String[] tmp=i.split(":");
				try{
					if (tmp.length==2){
						if (tmp[0].equals("tab")){
							SikuliIDE.getInstance().autoSelectTab(Integer.parseInt(tmp[1]));
							
					}else if(tmp[0].equals("Func")){
						jumpTo(tmp[1]);
					}else if (tmp[0].equals("line")){
						jumpTo(Integer.parseInt(tmp[1]));
					}
				}
					
				}catch(Exception e){
					
				}
			}
		}
		  
	  });
	  menu.add(back);
	  menu.addSeparator();
	  JMenuItem updateMenu=new JMenuItem("update outline");
	  updateMenu.addActionListener(new ActionListener(){

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			try{
			String text=getDocument().getText(0, getDocument().getLength());
			createOutLineMenu(text);
			}catch(Exception e){}
		}
		  
	  });
	  menu.add(updateMenu);
	  menu.addSeparator();
 
   }
        private void createOutLineMenu(String text){
	   JComboBox jcb=new JComboBox();
	   outline=new BasicComboPopup(jcb);
	   DefaultComboBoxModel dm=new DefaultComboBoxModel();

	   String[] tmp=text.split("\n");
	    ArrayList<String> attr=new ArrayList<String>();
	   ArrayList<String> func=new ArrayList<String>();
	   for(int i=0;i<tmp.length;i++){
		   if (!tmp[i].startsWith(" ") && !tmp[i].startsWith("#")){
			   if ((tmp[i].indexOf("=")!=-1) && (tmp[i].indexOf("==")==-1) && (tmp[i].indexOf("!=")==-1)){
				   String[] att=tmp[i].split("=");
				   if( att[0].indexOf(".")==-1)
				   attr.add(att[0]);
			   }
			   if (tmp[i].startsWith("def ") && tmp[i].indexOf("(")!=-1){
				   tmp[i]=tmp[i].substring(4,tmp[i].length()-1).trim();
				   func.add(tmp[i]);
			   }
		   }
	   }
	  
	   for(int i=0;i<attr.size();i++){

		   dm.addElement(attr.get(i));
	   }

	   for(int i=0;i<func.size();i++){
		  
		   dm.addElement(func.get(i));
	   }
	   jcb.addItemListener(new ItemListener(){

		@Override
		public void itemStateChanged(ItemEvent arg0) {
			// TODO Auto-generated method stub
			
			insertString(EditorPane.this.getCaretPosition(),arg0.getItem().toString());
			outline.hide();
		}
		   
	   });
	   jcb.setModel(dm);
	   if ((attr.size()+func.size())>0)
	   jcb.setSelectedIndex(0);
   }
        @Override
        public void keyReleased(java.awt.event.KeyEvent ke) {
	   System.out.println(ke.getKeyCode()+"="+ke.getKeyChar()+"  sblen="+sb.size());
	   if (ke.getKeyChar()=='.'){
		  cstring=sb.peek();
                  if (cstring.length()<4){
                      return;
                  }
		   String t=cstring.substring(cstring.length()-4);
		   if(t.toString().equals("self")){
			   try{
			   Rectangle caretRect=EditorPane.this.modelToView(EditorPane.this.getCaretPosition());
			   
			   if (outline==null) {
				   createOutLineMenu(getDocument().getText(0, getDocument().getLength()));  
				   EditorPane.this.add(outline);
				   outline.setVisible(false);
				   }
			   
			   
			  
			   outline.show(EditorPane.this,caretRect.x,caretRect.y );
			   
			   

			   //System.out.println(outline.requestFocusInWindow());
			   }catch(Exception ex){ex.printStackTrace();}
		   }
		  
	   }
	   if (ke.getKeyCode()==KeyEvent.VK_BACK_SPACE && !sb.empty()){
		   //sb=sb.substring(0,sb.length()-1);
                   sb.pop();
		   
	   }else if (ke.getKeyCode()==KeyEvent.VK_UP || ke.getKeyCode()==KeyEvent.VK_LEFT || ke.getKeyCode()==KeyEvent.VK_DOWN
			   || ke.getKeyCode()==KeyEvent.VK_RIGHT || ke.getKeyCode()==KeyEvent.VK_TAB){
		   if (ke.getKeyCode()==KeyEvent.VK_TAB){
			   sb.clear();
		   }
		   
	   }else{
                  if (sb.empty()){
                      sb.push(""+ke.getKeyChar());
                  }else{
                        sb.push(sb.peek()+ke.getKeyChar());
                  }
	   }
	  
	  if (sb.size()>50){
		  //sb=sb.substring(sb.length()-4);
              sb.clear();
	  }
   }
    private class MyActionLister extends MouseAdapter {

		@Override
		public void mouseClicked(MouseEvent e) {
			if (e.getButton()==e.BUTTON3){
				menu.show(e.getComponent(), e.getX(), e.getY());
			}
                        if(e.isControlDown() && (e.getButton() == e.BUTTON1) ){//for mac ctrl+left click
                            menu.show(e.getComponent(), e.getX(), e.getY());
                        }
			// TODO Auto-generated method stub
			super.mouseClicked(e);
		}
		   
	}
        private String createCaseTemplate(){
		String template="\n\n"+"def "+"<name>"+"(self):\n";
		String space="    ";
		template+=space+"import ForAll\n";
		template+=space+"try:\n";
		template+=space+space+"#Add your code here\n";
		template+=space+space+"\n";
		template+=space+space+"ForAll.log.info(\"Pass\")\n";
		template+=space+space+"ForAll.rmimg(ForAll.imgdir+\"/\"+sys._getframe().f_code.co_name+\".jpg\")\n";
		template+=space+"finally:\n";
		template+=space+space+"#Add your recovery env code\n";
		return template;
        }
         private String createSuiteTemplate(){
	   
	   String space="    ";
	   String template="from sikuli import *\n";
	   template+="import os,sys\n";
	   template+="home=os.environ['caseroot']\n";
	   template+="if not home in sys.path:\n";
	   template+=space+"sys.path.append(home)\n";
	   template+="#-------------------------------------------------Test PrePare------------------------------------\n";
	   template+="# Prepare Env\n";
	   template+="def setUp(self):\n";
	   template+=space+"import ForAll\n";
	   template+=space+"ForAll.setUp()\n";
	   template+="\n";
	   template+="# clean Env\n";
	   template+="def tearDown(self):\n";
	   template+=space+"import ForAll\n";
	   template+=space+"ForAll.tearDown()\n";
	   template+="#-------------------------------------------------------------------------------------------------\n";
	   template+="\n";
	   return template;
        }
	public String getSikuliContentType() {
		return sikuliContentType;
	}

	public SikuliIDEPopUpMenu getPopMenuImage() {
		return popMenuImage;
	}

	private void updateDocumentListeners(String source) {
		log(lvl, "updateDocumentListeners from: %s", source);
		if (dirtyHandler == null) {
			dirtyHandler = new DirtyHandler();
		}
		getDocument().addDocumentListener(dirtyHandler);
		getDocument().addUndoableEditListener(getUndoManager());
	}

	public EditorUndoManager getUndoManager() {
		if (_undo == null) {
			_undo = new EditorUndoManager();
		}
		return _undo;
	}

	public IIndentationLogic getIndentationLogic() {
		return _indentationLogic;
	}

	private void initKeyMap() {
		InputMap map = this.getInputMap();
		int shift = InputEvent.SHIFT_MASK;
		int ctrl = InputEvent.CTRL_MASK;
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, shift), SikuliEditorKit.deIndentAction);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, ctrl), SikuliEditorKit.deIndentAction);
	}

	@Override
	public void keyPressed(java.awt.event.KeyEvent ke) {
	}
/*
	@Override
	public void keyReleased(java.awt.event.KeyEvent ke) {
	}
*/
	@Override
	public void keyTyped(java.awt.event.KeyEvent ke) {
		//TODO implement code completion * checkCompletion(ke);
	}
	//</editor-fold>

	public String loadFile(boolean accessingAsFile) throws IOException {
		File file = new SikuliIDEFileChooser(sikuliIDE, accessingAsFile).load();
		if (file == null) {
			return null;
		}
		String fname = FileManager.slashify(file.getAbsolutePath(), false);
		int i = sikuliIDE.isAlreadyOpen(fname);
		if (i > -1) {
			log(lvl, "loadFile: Already open in IDE: " + fname);
			return null;
		}
		loadFile(fname);
		if (_editingFile == null) {
			return null;
		}
		return fname;
	}

	public void loadFile(String filename) {
		log(lvl, "loadfile: %s", filename);
		filename = FileManager.slashify(filename, false);
		File script = new File(filename);
		setSrcBundle(filename + "/");
		_editingFile = Runner.getScriptFile(script);
		if (_editingFile != null) {
			scriptType = _editingFile.getAbsolutePath().substring(_editingFile.getAbsolutePath().lastIndexOf(".") + 1);
			initBeforeLoad(scriptType);
			if (!readScript(_editingFile)) {
				_editingFile = null;
			}
			updateDocumentListeners("loadFile");
			setDirty(false);
		}
		if (_editingFile == null) {
			_srcBundlePath = null;
		} else {
			_srcBundleTemp = false;
		}
	}

	private boolean readScript(Object script) {
		InputStreamReader isr;
		try {
			if (script instanceof String) {
				isr = new InputStreamReader(
					new ByteArrayInputStream(((String) script).getBytes(Charset.forName("utf-8"))),
					Charset.forName("utf-8"));
			} else if (script instanceof File) {
				isr = new InputStreamReader(
					new FileInputStream((File) script),
					Charset.forName("utf-8"));
			} else {
				log(-1, "readScript: not supported %s as %s", script, script.getClass());
				return false;
			}
			this.read(new BufferedReader(isr), null);
		} catch (Exception ex) {
			log(-1, "read returned %s", ex.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public void read(Reader in, Object desc) throws IOException {
		super.read(in, desc);
		Document doc = getDocument();
		Element root = doc.getDefaultRootElement();
		parse(root);
		restoreCaretPosition();
	}

	public boolean hasEditingFile() {
		return _editingFile != null;
	}

	public String saveFile() throws IOException {
		if (_editingFile == null) {
			return saveAsFile(Settings.isMac());
		} else {
			writeSrcFile();
			return getCurrentShortFilename();
		}
	}

	public String saveAsFile(boolean accessingAsFile) throws IOException {
		File file = new SikuliIDEFileChooser(sikuliIDE, accessingAsFile).save();
		if (file == null) {
			return null;
		}
		String bundlePath = FileManager.slashify(file.getAbsolutePath(), false);
		if (!file.getAbsolutePath().endsWith(".sikuli")) {
			bundlePath += ".sikuli";
		}
		if (FileManager.exists(bundlePath)) {
			int res = JOptionPane.showConfirmDialog(
							null, SikuliIDEI18N._I("msgFileExists", bundlePath),
							SikuliIDEI18N._I("dlgFileExists"), JOptionPane.YES_NO_OPTION);
			if (res != JOptionPane.YES_OPTION) {
				return null;
			}
			FileManager.deleteFileOrFolder(bundlePath);
		}
		FileManager.mkdir(bundlePath);
		try {
			saveAsBundle(bundlePath, (sikuliIDE.getCurrentFileTabTitle()));
			if (Settings.isMac()) {
				if (!Settings.handlesMacBundles) {
					makeBundle(bundlePath, accessingAsFile);
				}
			}
		} catch (IOException iOException) {
		}
		return getCurrentShortFilename();
	}

	private void makeBundle(String path, boolean asFile) {
		String isBundle = asFile ? "B" : "b";
		String result = Sikulix.run(new String[]{"#SetFile", "-a", isBundle, path});
		if (!result.isEmpty()) {
			log(-1, "makeBundle: return: " + result);
		}
		if (asFile) {
			if (!FileManager.writeStringToFile("/Applications/SikuliX-IDE.app",
							(new File(path, ".LSOverride")).getAbsolutePath())) {
				log(-1, "makeBundle: not possible: .LSOverride");
			}
		} else {
			new File(path, ".LSOverride").delete();
		}
	}

	private void saveAsBundle(String bundlePath, String current) throws IOException {
//TODO allow other file types
		log(lvl, "saveAsBundle: " + getSrcBundle());
		bundlePath = FileManager.slashify(bundlePath, true);
		if (_srcBundlePath != null) {
			if (!ScriptingSupport.transferScript(_srcBundlePath, bundlePath)) {
				log(-1, "saveAsBundle: did not work - ");
			}
		}
		ImagePath.remove(_srcBundlePath);
		if (_srcBundleTemp) {
			FileManager.deleteTempDir(_srcBundlePath);
			_srcBundleTemp = false;
		}
		setSrcBundle(bundlePath);
		_editingFile = createSourceFile(bundlePath, "." + Runner.typeEndings.get(sikuliContentType));
		writeSrcFile();
		reparse();
	}

	private File createSourceFile(String bundlePath, String ext) {
		if (ext != null) {
			String name = new File(bundlePath).getName();
			name = name.substring(0, name.lastIndexOf("."));
			return new File(bundlePath, name + ext);
		} else {
			return new File(bundlePath);
		}
	}

	private void writeSrcFile() throws IOException {
		log(lvl, "writeSrcFile: " + _editingFile.getName());
		writeFile(_editingFile.getAbsolutePath());
		if (PreferencesUser.getInstance().getAtSaveMakeHTML()) {
			convertSrcToHtml(getSrcBundle());
		} else {
			String snameDir = new File(_editingFile.getAbsolutePath()).getParentFile().getName();
			String sname = snameDir.replace(".sikuli", "") + ".html";
			(new File(snameDir, sname)).delete();
		}
		if (PreferencesUser.getInstance().getAtSaveCleanBundle()) {
			if (!sikuliContentType.equals(Runner.CPYTHON)) {
				log(lvl, "delete-not-used-images for %s using Python string syntax", sikuliContentType);
			}
			cleanBundle();
		}
		setDirty(false);
	}

	private void cleanBundle() {
		log(3, "cleanBundle");
		FileManager.deleteNotUsedImages(getBundlePath(), parseforImages().keySet());
		log(3, "cleanBundle finished");
	}

	private Map<String, List<Integer>> parseforImages() {
		String pbundle = FileManager.slashify(getSrcBundle(), false);
		log(3, "parseforImages: in \n%s", pbundle);
		String scriptText = getText();
		Lexer lexer = getLexer();
		Map<String, List<Integer>> images = new HashMap<String, List<Integer>>();
		parseforImagesWalk(pbundle, lexer, scriptText, 0, images);
		log(3, "parseforImages finished");
		return images;
	}

	private void parseforImagesWalk(String pbundle, Lexer lexer,
					String text, int pos, Map<String, List<Integer>> images) {
		//log(3, "parseforImagesWalk");
		Iterable<Token> tokens = lexer.getTokens(text);
		boolean inString = false;
		String current;
		String innerText;
		String[] possibleImage = new String[]{""};
		String[] stringType = new String[]{""};
		for (Token t : tokens) {
			current = t.getValue();
			if (t.getType() == TokenType.Comment) {
    		//log(3, "parseforImagesWalk::Comment");
				innerText = t.getValue().substring(1);
				parseforImagesWalk(pbundle, lexer, innerText, t.getPos() + 1, images);
				continue;
			}
			if (t.getType() == TokenType.String_Doc) {
    		//log(3, "parseforImagesWalk::String_Doc");
				innerText = t.getValue().substring(3, t.getValue().length() - 3);
				parseforImagesWalk(pbundle, lexer, innerText, t.getPos() + 3, images);
				continue;
			}
			if (!inString) {
				inString = parseforImagesGetName(current, inString, possibleImage, stringType);
				continue;
			}
			if (!parseforImagesGetName(current, inString, possibleImage, stringType)) {
				inString = false;
				parseforImagesCollect(pbundle, possibleImage[0], pos + t.getPos(), images);
				continue;
			}
		}
	}

	private boolean parseforImagesGetName(String current, boolean inString,
					String[] possibleImage, String[] stringType) {
		//log(3, "parseforImagesGetName (inString: %s)", inString);
		if (!inString) {
			if (!current.isEmpty() && (current.contains("\"") || current.contains("'"))) {
				possibleImage[0] = "";
				stringType[0] = current.substring(current.length() - 1, current.length());
				return true;
			}
		}
		if (!current.isEmpty() && "'\"".contains(current) && stringType[0].equals(current)) {
			return false;
		}
		if (inString) {
			possibleImage[0] += current;
		}
		return inString;
	}

	private void parseforImagesCollect(String pbundle, String img, int pos, Map<String, List<Integer>> images) {
		String fimg;
 		//log(3, "parseforImagesCollect");
		if (img.endsWith(".png") || img.endsWith(".jpg") || img.endsWith(".jpeg")) {
			fimg = FileManager.slashify(img, false);
			if (fimg.contains("/")) {
				if (!fimg.contains(pbundle)) {
					return;
				}
				img = new File(fimg).getName();
			}
			if (images.containsKey(img)) {
				images.get(img).add(pos);
			} else {
				List<Integer> poss = new ArrayList<Integer>();
				poss.add(pos);
				images.put(img, poss);
			}
		}
	}

	private Lexer getLexer() {
//TODO this only works for cleanbundle to find the image strings
		String scriptType = "python";
		if (null != lexers.get(scriptType)) {
			return lexers.get(scriptType);
		}
		try {
			Lexer lexer = Lexer.getByName(scriptType);
			lexers.put(scriptType, lexer);
			return lexer;
		} catch (ResolutionException ex) {
			return null;
		}
	}

	public String exportAsZip() throws IOException, FileNotFoundException {
		File file = new SikuliIDEFileChooser(sikuliIDE).export();
		if (file == null) {
			return null;
		}

		String zipPath = file.getAbsolutePath();
		String srcName = file.getName();
		if (!file.getAbsolutePath().endsWith(".skl")) {
			zipPath += ".skl";
		} else {
			srcName = srcName.substring(0, srcName.lastIndexOf('.'));
		}
		writeFile(getSrcBundle() + srcName + ".py");
		FileManager.zip(getSrcBundle(), zipPath);
		log(lvl, "exportAsZip: " + zipPath);
		return zipPath;
	}

	private void writeFile(String filename) throws IOException {
		this.write(new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(filename), "UTF8")));
	}

	public boolean close() throws IOException {
		log(lvl, "Tab close clicked");
		if (isDirty()) {
			Object[] options = {SikuliIDEI18N._I("yes"), SikuliIDEI18N._I("no"), SikuliIDEI18N._I("cancel")};
			int ans = JOptionPane.showOptionDialog(this,
							SikuliIDEI18N._I("msgAskSaveChanges", getCurrentShortFilename()),
							SikuliIDEI18N._I("dlgAskCloseTab"),
							JOptionPane.YES_NO_CANCEL_OPTION,
							JOptionPane.WARNING_MESSAGE,
							null,
							options, options[0]);
			if (ans == JOptionPane.CANCEL_OPTION
							|| ans == JOptionPane.CLOSED_OPTION) {
				return false;
			} else if (ans == JOptionPane.YES_OPTION) {
				if (saveFile() == null) {
					return false;
				}
			} else {
//				sikuliIDE.getTabPane().resetLastClosed();
			}
			setDirty(false);
		}
		if (_srcBundlePath != null) {
			ImagePath.remove(_srcBundlePath);
			if (_srcBundleTemp) {
				FileManager.deleteTempDir(_srcBundlePath);
			}
		}
		return true;
	}

	private void setSrcBundle(String newBundlePath) {
    try {
      newBundlePath = new File(newBundlePath).getCanonicalPath();
    } catch (Exception ex) {
      return;
    }
		_srcBundlePath = newBundlePath;
		ImagePath.setBundlePath(_srcBundlePath);
	}

	public String getSrcBundle() {
		if (_srcBundlePath == null) {
			File tmp = FileManager.createTempDir();
			setSrcBundle(FileManager.slashify(tmp.getAbsolutePath(), true));
			_srcBundleTemp = true;
		}
		return _srcBundlePath;
	}

	// used at ButtonRun.run
	public String getBundlePath() {
		return _srcBundlePath;
	}

	public boolean isSourceBundleTemp() {
		return _srcBundleTemp;
	}

	public String getCurrentSrcDir() {
		if (_srcBundlePath != null) {
			if (_editingFile == null || _srcBundleTemp) {
				return FileManager.normalize(_srcBundlePath);
			} else {
				return _editingFile.getParent();
			}
		} else {
			return null;
		}
	}

	public String getCurrentShortFilename() {
		if (_srcBundlePath != null) {
			File f = new File(_srcBundlePath);
			return f.getName();
		}
		return "Untitled";
	}

	public File getCurrentFile() {
		return getCurrentFile(true);
	}

	public File getCurrentFile(boolean shouldSave) {
		if (shouldSave && _editingFile == null && isDirty()) {
			try {
				saveAsFile(Settings.isMac());
			} catch (IOException e) {
				log(-1, "getCurrentFile: Problem while trying to save %s\n%s",
								_editingFile.getAbsolutePath(), e.getMessage());
			}
		}
		return _editingFile;
	}

	public String getCurrentFilename() {
		if (_editingFile == null) {
			return null;
		}
		return _editingFile.getAbsolutePath();
	}

//TODO convertSrcToHtml has to be completely revised
	private void convertSrcToHtml(String bundle) {
		IScriptRunner runner = ScriptingSupport.getRunner(null, "jython");
		if (runner != null) {
			runner.doSomethingSpecial("convertSrcToHtml", new String[]{bundle});
		}
	}

	public File copyFileToBundle(String filename) {
		File f = new File(filename);
		String bundlePath = getSrcBundle();
		if (f.exists()) {
			try {
				File newFile = FileManager.smartCopy(filename, bundlePath);
				return newFile;
			} catch (IOException e) {
				log(-1, "copyFileToBundle: Problem while trying to save %s\n%s",
								filename, e.getMessage());
				return f;
			}
		}
		return null;
	}

	public Image getImageInBundle(String filename) {
		return Image.createThumbNail(filename);
	}

	//<editor-fold defaultstate="collapsed" desc="Dirty handling">
	public boolean isDirty() {
		return scriptIsDirty;
	}

	public void setDirty(boolean flag) {
		if (scriptIsDirty == flag) {
			return;
		}
		scriptIsDirty = flag;
		sikuliIDE.setCurrentFileTabTitleDirty(scriptIsDirty);
	}

	private class DirtyHandler implements DocumentListener {

		@Override
		public void changedUpdate(DocumentEvent ev) {
			log(lvl + 1, "change update");
			//setDirty(true);
		}

		@Override
		public void insertUpdate(DocumentEvent ev) {
			log(lvl + 1, "insert update");
			setDirty(true);
		}

		@Override
		public void removeUpdate(DocumentEvent ev) {
			log(lvl + 1, "remove update");
			setDirty(true);
		}
	}

	//</editor-fold>
	//<editor-fold defaultstate="collapsed" desc="Caret handling">
//TODO not used
	@Override
	public void caretUpdate(CaretEvent evt) {
		/* seems not to be used
		 * if (_can_update_caret_last_x) {
		 * _caret_last_x = -1;
		 * } else {
		 * _can_update_caret_last_x = true;
		 * }
		 */
	}

	public int getLineNumberAtCaret(int caretPosition) {
		Element root = getDocument().getDefaultRootElement();
		return root.getElementIndex(caretPosition) + 1;
	}

	public Element getLineAtCaret(int caretPosition) {
		Element root = getDocument().getDefaultRootElement();
    Element result;
		if (caretPosition == -1) {
			result = root.getElement(root.getElementIndex(getCaretPosition()));
		} else {
			result = root.getElement(root.getElementIndex(root.getElementIndex(caretPosition)));
		}
    return result;
	}

  public String getLineTextAtCaret() {
    Element elem = getLineAtCaret(-1);
    Document doc = elem.getDocument();
    Element subElem;
    String text;
    String line = "";
    int start = elem.getStartOffset();
    int end = elem.getEndOffset();
    for (int i = 0; i < elem.getElementCount(); i++) {
      text = "";
      subElem = elem.getElement(i);
      start = subElem.getStartOffset();
      end = subElem.getEndOffset();
      if (subElem.getName().contains("component")) {
        text = StyleConstants.getComponent(subElem.getAttributes()).toString();
      } else {
        try {
          text = doc.getText(start, end - start);
        } catch (Exception ex) {}
      }
      line  += text;
    }
    return line.trim();
  }

	public Element getLineAtPoint(MouseEvent me) {
		Point p = me.getLocationOnScreen();
		Point pp = getLocationOnScreen();
		p.translate(-pp.x, -pp.y);
		int pos = viewToModel(p);
		Element root = getDocument().getDefaultRootElement();
		int e = root.getElementIndex(pos);
		if (e == -1) {
			return null;
		}
		return root.getElement(e);
	}

	public boolean jumpTo(int lineNo, int column) {
		log(lvl + 1, "jumpTo pos: " + lineNo + "," + column);
		try {
			int off = getLineStartOffset(lineNo - 1) + column - 1;
			int lineCount = getDocument().getDefaultRootElement().getElementCount();
			if (lineNo < lineCount) {
				int nextLine = getLineStartOffset(lineNo);
				if (off >= nextLine) {
					off = nextLine - 1;
				}
			}
			if (off >= 0) {
				setCaretPosition(off);
			}
		} catch (BadLocationException ex) {
			jumpTo(lineNo);
			return false;
		}
		return true;
	}

	public boolean jumpTo(int lineNo) {
		log(lvl + 1, "jumpTo line: " + lineNo);
		try {
			setCaretPosition(getLineStartOffset(lineNo - 1));
		} catch (BadLocationException ex) {
			return false;
		}
		return true;
	}

	public int getLineStartOffset(int line) throws BadLocationException {
		// line starting from 0
		Element map = getDocument().getDefaultRootElement();
		if (line < 0) {
			throw new BadLocationException("Negative line", -1);
		} else if (line >= map.getElementCount()) {
			throw new BadLocationException("No such line", getDocument().getLength() + 1);
		} else {
			Element lineElem = map.getElement(line);
			return lineElem.getStartOffset();
		}
	}

	//<editor-fold defaultstate="collapsed" desc="TODO only used for UnitTest">
	public void jumpTo(String funcName) throws BadLocationException {
		log(lvl + 1, "jumpTo function: " + funcName);
		Element root = getDocument().getDefaultRootElement();
		int pos = getFunctionStartOffset(funcName, root);
		if (pos >= 0) {
			setCaretPosition(pos);
		} else {
			throw new BadLocationException("Can't find function " + funcName, -1);
		}
	}

	private int getFunctionStartOffset(String func, Element node) throws BadLocationException {
		Document doc = getDocument();
		int count = node.getElementCount();
		Pattern patDef = Pattern.compile("def\\s+" + func + "\\s*\\(");
		for (int i = 0; i < count; i++) {
			Element elm = node.getElement(i);
			if (elm.isLeaf()) {
				int start = elm.getStartOffset(), end = elm.getEndOffset();
				String line = doc.getText(start, end - start);
				Matcher matcher = patDef.matcher(line);
				if (matcher.find()) {
					return start;
				}
			} else {
				int p = getFunctionStartOffset(func, elm);
				if (p >= 0) {
					return p;
				}
			}
		}
		return -1;
	}
  //</editor-fold>
	//</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="replace text patterns with image buttons">
	public boolean reparse(String oldName, String newName, boolean fileOverWritten) {
		boolean success;
		if (fileOverWritten) {
			Image.unCacheBundledImage(newName);
		}
    Map<String,List<Integer>> images = parseforImages();
    oldName = new File(oldName).getName();
    List<Integer> poss = images.get(oldName);
    if (images.containsKey(oldName) && poss.size() > 0) {
      Collections.sort(poss, new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
          if (o1 > o2) return -1;
          return 1;
        }
      });
			reparseRenameImages(poss, oldName, new File(newName).getName());
    }
		return reparse();
	}

	private boolean reparseRenameImages(List<Integer> poss, String oldName, String newName) {
		StringBuilder text = new StringBuilder(getText());
		int lenOld = oldName.length();
		for (int pos : poss) {
			text.replace(pos - lenOld, pos, newName);
		}
		setText(text.toString());
		return true;
	}

	public boolean reparse() {
		String paneContent = this.getText();
		if (paneContent.length() < 7) {
			return true;
		}
		if (readScript(paneContent)) {
			updateDocumentListeners("reparse");
			return true;
		}
		return false;
	}

//TODO not clear what this is for LOL (SikuliIDEPopUpMenu.doSetType)
	public boolean reparseCheckContent() {
		if (this.getText().contains(ScriptingSupport.TypeCommentToken)) {
			return true;
		}
		return false;
	}

	private void parse(Element node) {
		if (!showThumbs) {
			// do not show any thumbnails
			return;
		}
		int count = node.getElementCount();
		for (int i = 0; i < count; i++) {
			Element elm = node.getElement(i);
			log(lvl + 1, elm.toString());
			if (elm.isLeaf()) {
				int start = elm.getStartOffset(), end = elm.getEndOffset();
				parseRange(start, end);
			} else {
				parse(elm);
			}
		}
	}

  public String parseLineText(String line) {
    if (line.startsWith("#")) {
      Pattern aName = Pattern.compile("^#[A-Za-z0-9_]+ =$");
      Matcher mN = aName.matcher(line);
      if (mN.find()) {
        return line.substring(1).split(" ")[0];
      }
      return "";
    }
    Matcher mR = patRegionStr.matcher(line);
    String asOffset = ".asOffset()";
    if (mR.find()) {
      if (line.length() >= mR.end() + asOffset.length()) {
        if (line.substring(mR.end()).contains(asOffset)) {
          return line.substring(mR.start(), mR.end()) + asOffset;
        }
      }
      return line.substring(mR.start(), mR.end());
    }
    Matcher mL = patLocationStr.matcher(line);
    if (mL.find()) {
      return line.substring(mL.start(), mL.end());
    }
    Matcher mP = patPatternStr.matcher(line);
    if (mP.find()) {
      return line.substring(mP.start(), mP.end());
    }
    Matcher mI = patPngStr.matcher(line);
    if (mI.find()) {
      return line.substring(mI.start(), mI.end());
    }
    return "";
  }

  private int parseRange(int start, int end) {
		if (!showThumbs) {
			// do not show any thumbnails
			return end;
		}
		try {
			end = parseLine(start, end, patCaptureBtn);
			end = parseLine(start, end, patPatternStr);
			end = parseLine(start, end, patRegionStr);
			end = parseLine(start, end, patPngStr);
		} catch (BadLocationException e) {
			log(-1, "parseRange: Problem while trying to parse line\n%s", e.getMessage());
		}
		return end;
	}

	private int parseLine(int startOff, int endOff, Pattern ptn) throws BadLocationException {
		if (endOff <= startOff) {
			return endOff;
		}
		Document doc = getDocument();
		while (true) {
			String line = doc.getText(startOff, endOff - startOff);
			Matcher m = ptn.matcher(line);
			//System.out.println("["+line+"]");
			if (m.find()) {
				int len = m.end() - m.start();
				boolean replaced = replaceWithImage(startOff + m.start(), startOff + m.end(), ptn);
				if (replaced) {
					startOff += m.start() + 1;
					endOff -= len - 1;
				} else {
					startOff += m.end() + 1;
				}
			} else {
				break;
			}
		}
		return endOff;
	}

	private boolean replaceWithImage(int startOff, int endOff, Pattern ptn)
					throws BadLocationException {
		Document doc = getDocument();
		String imgStr = doc.getText(startOff, endOff - startOff);
		JComponent comp = null;

		if (ptn == patPatternStr || ptn == patPngStr) {
			if (pref.getPrefMoreImageThumbs()) {
				comp = EditorPatternButton.createFromString(this, imgStr, null);
			} else {
				comp = EditorPatternLabel.labelFromString(this, imgStr);
			}
		} else if (ptn == patRegionStr) {
			if (pref.getPrefMoreImageThumbs()) {
				comp = EditorRegionButton.createFromString(this, imgStr);
			} else {
				comp = EditorRegionLabel.labelFromString(this, imgStr);
			}
		} else if (ptn == patCaptureBtn) {
			comp = EditorPatternLabel.labelFromString(this, "");
		}
		if (comp != null) {
			this.select(startOff, endOff);
			this.insertComponent(comp);
			return true;
		}
		return false;
	}

	public String getRegionString(int x, int y, int w, int h) {
		return String.format("Region(%d,%d,%d,%d)", x, y, w, h);
	}

	public String getPatternString(String ifn, float sim, Location off, Image img) {
//TODO ifn really needed??
		if (ifn == null) {
			return "\"" + EditorPatternLabel.CAPTURE + "\"";
		}
		String imgName = new File(ifn).getName();
		if (img != null) {
			imgName = img.getName();
		}
		String pat = "Pattern(\"" + imgName + "\")";
		String ret = "";
		if (sim > 0) {
			if (sim >= 0.99F) {
				ret += ".exact()";
			} else if (sim != 0.7F) {
				ret += String.format(Locale.ENGLISH, ".similar(%.2f)", sim);
			}
		}
		if (off != null && (off.x != 0 || off.y != 0)) {
			ret += ".targetOffset(" + off.x + "," + off.y + ")";
		}
		if (!ret.equals("")) {
			ret = pat + ret;
		} else {
			ret = "\"" + imgName + "\"";
		}
		return ret;
	}
  //</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="content insert append">
	public void insertString(String str) {
		int sel_start = getSelectionStart();
		int sel_end = getSelectionEnd();
		if (sel_end != sel_start) {
			try {
				getDocument().remove(sel_start, sel_end - sel_start);
			} catch (BadLocationException e) {
				log(-1, "insertString: Problem while trying to insert\n%s", e.getMessage());
			}
		}
		int pos = getCaretPosition();
		insertString(pos, str);
		int new_pos = getCaretPosition();
		int end = parseRange(pos, new_pos);
		setCaretPosition(end);
	}

	private void insertString(int pos, String str) {
		Document doc = getDocument();
		try {
			doc.insertString(pos, str, null);
		} catch (Exception e) {
			log(-1, "insertString: Problem while trying to insert at pos\n%s", e.getMessage());
		}
	}

//TODO not used
	public void appendString(String str) {
		Document doc = getDocument();
		try {
			int start = doc.getLength();
			doc.insertString(doc.getLength(), str, null);
			int end = doc.getLength();
			//end = parseLine(start, end, patHistoryBtnStr);
		} catch (Exception e) {
			log(-1, "appendString: Problem while trying to append\n%s", e.getMessage());
		}
	}
  //</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="feature search">
  /*
	 * public int search(Pattern pattern){
	 * return search(pattern, true);
	 * }
	 *
	 * public int search(Pattern pattern, boolean forward){
	 * if(!pattern.equals(_lastSearchPattern)){
	 * _lastSearchPattern = pattern;
	 * Document doc = getDocument();
	 * int pos = getCaretPosition();
	 * Debug.log("caret: "  + pos);
	 * try{
	 * String body = doc.getText(pos, doc.getLength()-pos);
	 * _lastSearchMatcher = pattern.matcher(body);
	 * }
	 * catch(BadLocationException e){
	 * e.printStackTrace();
	 * }
	 * }
	 * return continueSearch(forward);
	 * }
	 */

	/*
	 * public int search(String str){
	 * return search(str, true);
	 * }
	 */
	public int search(String str, int pos, boolean forward) {
		boolean isCaseSensitive = true;
		String toSearch = str;
		if (str.startsWith("!")) {
			str = str.substring(1).toUpperCase();
			isCaseSensitive = false;
		}
		int ret = -1;
		Document doc = getDocument();
		Debug.log(lvl, "search: %s from %d forward: %s", str, pos, forward);
		try {
			String body;
			int begin;
			if (forward) {
				int len = doc.getLength() - pos;
				body = doc.getText(pos, len > 0 ? len : 0);
				begin = pos;
			} else {
				body = doc.getText(0, pos);
				begin = 0;
			}
			if (!isCaseSensitive) {
				body = body.toUpperCase();
			}
			Pattern pattern = Pattern.compile(Pattern.quote(str));
			Matcher matcher = pattern.matcher(body);
			ret = continueSearch(matcher, begin, forward);
			if (ret < 0) {
				if (forward && pos != 0) {
					return search(toSearch, 0, forward);
				}
				if (!forward && pos != doc.getLength()) {
					return search(toSearch, doc.getLength(), forward);
				}
			}
		} catch (BadLocationException e) {
			log(-1, "search: did not work:\n" + e.getStackTrace());
		}
		return ret;
	}

	protected int continueSearch(Matcher matcher, int pos, boolean forward) {
		boolean hasNext = false;
		int start = 0, end = 0;
		if (!forward) {
			while (matcher.find()) {
				hasNext = true;
				start = matcher.start();
				end = matcher.end();
			}
		} else {
			hasNext = matcher.find();
			if (!hasNext) {
				return -1;
			}
			start = matcher.start();
			end = matcher.end();
		}
		if (hasNext) {
			Document doc = getDocument();
			getCaret().setDot(pos + end);
			getCaret().moveDot(pos + start);
			getCaret().setSelectionVisible(true);
			return pos + start;
		}
		return -1;
	}
  //</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="Transfer code incl. images between code panes">
	private class MyTransferHandler extends TransferHandler {

		private static final String me = "EditorPaneTransferHandler: ";
		Map<String, String> _copiedImgs = new HashMap<String, String>();

		@Override
		public void exportToClipboard(JComponent comp, Clipboard clip, int action) {
			super.exportToClipboard(comp, clip, action);
		}

		@Override
		protected void exportDone(JComponent source,
						Transferable data,
						int action) {
			if (action == TransferHandler.MOVE) {
				JTextPane aTextPane = (JTextPane) source;
				int sel_start = aTextPane.getSelectionStart();
				int sel_end = aTextPane.getSelectionEnd();
				Document doc = aTextPane.getDocument();
				try {
					doc.remove(sel_start, sel_end - sel_start);
				} catch (BadLocationException e) {
					log(-1, "MyTransferHandler: exportDone: Problem while trying to remove text\n%s", e.getMessage());
				}
			}
		}

		@Override
		public int getSourceActions(JComponent c) {
			return COPY_OR_MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			JTextPane aTextPane = (JTextPane) c;

			SikuliEditorKit kit = ((SikuliEditorKit) aTextPane.getEditorKit());
			Document doc = aTextPane.getDocument();
			int sel_start = aTextPane.getSelectionStart();
			int sel_end = aTextPane.getSelectionEnd();

			StringWriter writer = new StringWriter();
			try {
				_copiedImgs.clear();
				kit.write(writer, doc, sel_start, sel_end - sel_start, _copiedImgs);
				return new StringSelection(writer.toString());
			} catch (Exception e) {
				log(-1, "MyTransferHandler: createTransferable: Problem creating text to copy\n%s", e.getMessage());
			}
			return null;
		}

		@Override
		public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
			for (int i = 0; i < transferFlavors.length; i++) {
				//System.out.println(transferFlavors[i]);
				if (transferFlavors[i].equals(DataFlavor.stringFlavor)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean importData(JComponent comp, Transferable t) {
			DataFlavor htmlFlavor = DataFlavor.stringFlavor;
			if (canImport(comp, t.getTransferDataFlavors())) {
				try {
					String transferString = (String) t.getTransferData(htmlFlavor);
					EditorPane targetTextPane = (EditorPane) comp;
					for (Map.Entry<String, String> entry : _copiedImgs.entrySet()) {
						String imgName = entry.getKey();
						String imgPath = entry.getValue();
						File destFile = targetTextPane.copyFileToBundle(imgPath);
						String newName = destFile.getName();
						if (!newName.equals(imgName)) {
							String ptnImgName = "\"" + imgName + "\"";
							newName = "\"" + newName + "\"";
							transferString = transferString.replaceAll(ptnImgName, newName);
							Debug.info("MyTransferHandler: importData:" + ptnImgName + " exists. Rename it to " + newName);
						}
					}
					targetTextPane.insertString(transferString);
				} catch (Exception e) {
					log(-1, "MyTransferHandler: importData: Problem pasting text\n%s", e.getMessage());
				}
				return true;
			}
			return false;
		}
	}
//</editor-fold>

	//<editor-fold defaultstate="collapsed" desc="currently not used">
	private String _tabString = "   ";

	private void setTabSize(int charactersPerTab) {
		FontMetrics fm = this.getFontMetrics(this.getFont());
		int charWidth = fm.charWidth('w');
		int tabWidth = charWidth * charactersPerTab;

		TabStop[] tabs = new TabStop[10];

		for (int j = 0; j < tabs.length; j++) {
			int tab = j + 1;
			tabs[j] = new TabStop(tab * tabWidth);
		}

		TabSet tabSet = new TabSet(tabs);
		SimpleAttributeSet attributes = new SimpleAttributeSet();
		StyleConstants.setFontSize(attributes, 18);
		StyleConstants.setFontFamily(attributes, "Osaka-Mono");
		StyleConstants.setTabSet(attributes, tabSet);
		int length = getDocument().getLength();
		getStyledDocument().setParagraphAttributes(0, length, attributes, true);
	}

	private void setTabs(int spaceForTab) {
		String t = "";
		for (int i = 0; i < spaceForTab; i++) {
			t += " ";
		}
		_tabString = t;
	}

	private void expandTab() throws BadLocationException {
		int pos = getCaretPosition();
		Document doc = getDocument();
		doc.remove(pos - 1, 1);
		doc.insertString(pos - 1, _tabString, null);
	}
	private Class _historyBtnClass;

	private void setHistoryCaptureButton(ButtonCapture btn) {
		_historyBtnClass = btn.getClass();
	}

	private void indent(int startLine, int endLine, int level) {
		Document doc = getDocument();
		String strIndent = "";
		if (level > 0) {
			for (int i = 0; i < level; i++) {
				strIndent += "  ";
			}
		} else {
			Debug.error("negative indentation not supported yet!!");
		}
		for (int i = startLine; i < endLine; i++) {
			try {
				int off = getLineStartOffset(i);
				if (level > 0) {
					doc.insertString(off, strIndent, null);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void checkCompletion(java.awt.event.KeyEvent ke) throws BadLocationException {
		Document doc = getDocument();
		Element root = doc.getDefaultRootElement();
		int pos = getCaretPosition();
		int lineIdx = root.getElementIndex(pos);
		Element line = root.getElement(lineIdx);
		int start = line.getStartOffset(), len = line.getEndOffset() - start;
		String strLine = doc.getText(start, len - 1);
		Debug.log(9, "[" + strLine + "]");
		if (strLine.endsWith("find") && ke.getKeyChar() == '(') {
			ke.consume();
			doc.insertString(pos, "(", null);
			ButtonCapture btnCapture = new ButtonCapture(this, line);
			insertComponent(btnCapture);
			doc.insertString(pos + 2, ")", null);
		}

	}
  //</editor-fold>
}
