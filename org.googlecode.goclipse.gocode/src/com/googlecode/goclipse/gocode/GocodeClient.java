package com.googlecode.goclipse.gocode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import com.googlecode.goclipse.Activator;
import com.googlecode.goclipse.Environment;
import com.googlecode.goclipse.builder.ExternalCommand;
import com.googlecode.goclipse.builder.ProcessOStreamFilter;
import com.googlecode.goclipse.builder.StreamAsLines;
import com.googlecode.goclipse.preferences.PreferenceConstants;

/**
 * Use the configured GoCode settings to call a GoCode client, which ends up talking to a running
 * GoCode server. This class is called from GoCodeContentAssistProcessor.
 */
public class GocodeClient {
  private String error;

  public GocodeClient() {
    
  }

  /**
   * @param fileName
   * @param bufferText
   * @param offset
   * @return
   */
  public List<String> getCompletions(IProject project, String fileName, final String bufferText, int offset) {
    error = null;
    
    String goroot = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOROOT);
    
    String goarch = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOARCH);
    String goos = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOOS);
    
    ExternalCommand goCodeCommand = buildGoCodeCommand();
    
    if (goCodeCommand == null) {
      return Collections.emptyList();
    }

    goCodeCommand.setTimeout(100);

    // set the package path for the current project
    List<String> parameters = new LinkedList<String>();
    if (GocodePlugin.USE_TCP) {
      parameters.add("-sock=tcp");
    }
    parameters.add("set");
    parameters.add("lib-path");

    IPath rootPath = new Path(goroot).append("pkg").append(goos + "_" + goarch);

    if (project == null) {
      parameters.add(rootPath.toOSString());
    } else {
      IPath projectPath = project.getLocation().append(Environment.INSTANCE.getPkgOutputFolder(project));

      parameters.add(rootPath.toOSString() + File.pathSeparatorChar + projectPath.toOSString());
    }
        
    goCodeCommand.execute(parameters);

    ExternalCommand command = buildGoCodeCommand();

    StreamAsLines output = new StreamAsLines();
    command.setResultsFilter(output);
    command.setInputFilter(new ProcessOStreamFilter() {
      @Override
      public void setStream(OutputStream outputStream) {
        OutputStreamWriter osw = new OutputStreamWriter(outputStream);
        try {
          osw.append(bufferText);
          osw.flush();
          outputStream.close();
        } catch (IOException e) {
          // do nothing
        }
      }
    });

    parameters = new LinkedList<String>();
    if (GocodePlugin.USE_TCP) {
      parameters.add("-sock=tcp");
    }
    parameters.add("-f=csv");
    parameters.add("autocomplete");
    parameters.add(fileName);
    parameters.add("c" + offset);
    error = command.execute(parameters, true);
    if (error != null) {
      String out = output.getLinesAsString();

      Activator.getDefault().getLog().log(
          new Status(IStatus.ERROR, Activator.PLUGIN_ID, out == null ? error : error + ": " + out));
    }

    return output.getLines();
  }

  protected String getError() {
    return error;
  }

  private ExternalCommand buildGoCodeCommand() {
    IPath gocodePath = GocodePlugin.getPlugin().getBestGocodeInstance();
    
    if (gocodePath == null) {
      return null;
    } else {
      return new ExternalCommand(gocodePath, true);
    }
  }

}
