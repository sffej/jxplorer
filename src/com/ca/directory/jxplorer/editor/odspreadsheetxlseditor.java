package com.ca.directory.jxplorer.editor;

import com.ca.commons.cbutil.CBIntText;

import java.awt.*;



/**
 * Class that extends BaseODMediaEditor to basically customise the display with
 * the title 'odSpreadSheetXLS' and to add a launch button that when clicked creates
 * a temp file and tries to launch the file in the default player.
 * @author Trudi.
 */
 
public class odspreadsheetxlseditor extends baseodmediaeditor
{

   /**
    * Calls the BaseODMediaEditor constructor, sets the dialog title, the file
    * name and the extension.
    * @param owner handle to the application frame, used to display dialog boxes.
    */

    public odspreadsheetxlseditor(Frame owner)
    {
        super(owner);
        super.setDialogTitle(CBIntText.get("odSpreadSheetXLS"));
        super.setFileName("odSpreadSheetXLS");
        super.setExtension(".xls");
    }
}