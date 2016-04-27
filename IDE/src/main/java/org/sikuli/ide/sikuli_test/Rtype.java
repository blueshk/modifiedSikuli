package org.sikuli.ide.sikuli_test;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.sikuli.ide.SikuliIDE;

public class Rtype extends JDialog {
	private ButtonGroup bg = new ButtonGroup();
	private JRadioButton all = new JRadioButton("run All method");
	private JRadioButton rf = new JRadioButton("run failed cases");
	private JRadioButton am = new JRadioButton("run specifed method:");
	private JFileChooser c = new JFileChooser();
	private JComboBox jb=new JComboBox();
	private JButton open = new JButton("open");
	private JButton ok = new JButton("ok");
	public File dirf=null; 
	UnitTestRunner test;
	public int type=0;
	public String method;
	private int ot;
	private String om;
	public Rtype(UnitTestRunner t,int ot,String om){
		this.test=t;
		this.ot=ot;
		this.om=om;
		
	}
	public Rtype(){
		
	}
	public void open() {
		setTitle("Run unit test case...");
		Container cp = getContentPane();
		cp.setLayout(new GridLayout(5, 1));

		if (ot==0){
			all.setSelected(true);
			type=0;
		}else if(ot==1){
			am.setSelected(true);
			type=1;
		}
		all.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				if(all.isSelected()){
					type=0;
					jb.setEnabled(false);
				}
			}
			
		});
		am.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				if(am.isSelected()){
					type=1;
					jb.setEnabled(true);
				}
			}
			
		});
		rf.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				if(rf.isSelected()){
					type=2;
					jb.setEnabled(false);
				}
			}
			
		});
		bg.add(all);
		bg.add(rf);
		bg.add(am);
		cp.add(all);
		cp.add(rf);
		cp.add(am);
		jb.removeAllItems();
	//	JPanel dcp = new JPanel();
	//	dcp.setLayout(new FlowLayout());
		setJb();
		if (ot==1){
			jb.setEnabled(true);
		}else{
			jb.setEnabled(false);
		}
		//jb.setMaximumRowCount(4);
		cp.add(jb);

		
		//c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		// c.showOpenDialog(this)
		
	/*	dcp.add(open);
		open.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				int ret = c.showOpenDialog(Rtype.this);
				if (ret == JFileChooser.APPROVE_OPTION) {
					File file = c.getSelectedFile();
					if (file.getAbsolutePath().equals("")){
						
					}else{

						dirf=file;
					}

				}
			}

		});
		// dcp.add(c);
		cp.add(dcp);*/
		JPanel bootp = new JPanel();
		bootp.setLayout(new FlowLayout());
		
		ok.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// TODO Auto-generated method stub
				Rtype.this.dispose();
				method=jb.getSelectedItem().toString();
				if (method==null || method.equals("")){
					method=null;
				}
			
				test.runSuite();
			
			}
			
		});
		bootp.add(ok);
		cp.add(bootp);
		
		setAlwaysOnTop(true);
		setSize(350, 160);
		//this.setAlwaysOnTop(true);
		setVisible(true);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

	}
	private void setJb(){
		 SikuliIDE ide = SikuliIDE.getInstance();
		 String filename = ide.getCurrentFilename();
     		try{
     		 BufferedReader in = new BufferedReader(new FileReader(filename));
     	      String line;
     	      String amethod;
     	      boolean sel=false;
     	      int num=0;
     	     while( (line = in.readLine()) != null ){
     	    	
     	    	 if (line.startsWith("def test_")){
     	    		 amethod=line.substring(4, line.indexOf("("));
     	    		 jb.addItem(amethod);
     	    		 if (ot==1 && om!=null & om.equals(amethod)){
     	    			 jb.setSelectedItem(amethod);
     	    			 sel=true;
     	    		 }
     	    		 num++;
     	    		
     	    	 }
     	     }
     	     if (!sel && num>0){
     	    	 jb.setSelectedIndex(0);
     	     }
     	     in.close();
     		}catch(Exception e){
     			e.printStackTrace();
     			
     		}
     	
	}
	public static void main(String arg[]){
		
		new Rtype().open();
	}
}
