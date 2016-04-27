package org.sikuli.ide;

import java.security.CodeSource;
import javax.swing.JOptionPane;

public class Sikulix {

  public static void main(String[] args) {
    String jarName = "";
    
    CodeSource codeSrc =  SikuliIDE.class.getProtectionDomain().getCodeSource();
    if (codeSrc != null && codeSrc.getLocation() != null) {
      jarName = codeSrc.getLocation().getPath();
    }

    if (jarName.contains("sikulixsetupIDE")) {
      JOptionPane.showMessageDialog(null, "Not useable!\nRun setup first!", 
              "sikulixsetupIDE", JOptionPane.ERROR_MESSAGE);
      System.exit(0);
    }
    SikuliIDE.run(args);
  }
}
