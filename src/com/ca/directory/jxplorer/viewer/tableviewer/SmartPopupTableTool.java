package com.ca.directory.jxplorer.viewer.tableviewer;

import com.ca.commons.cbutil.*;
import com.ca.commons.naming.DN;
import com.ca.commons.naming.RDN;
import com.ca.directory.jxplorer.JXplorer;
import com.ca.directory.jxplorer.search.SearchExecute;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This is the small popup menu that appears when a manager right-clicks (or system-dependant-whatever-s) on the
 * attribute editing table, allowing them to cut/copy/paste/delete/rename tree elements
 */
public class SmartPopupTableTool extends JPopupMenu
        implements ActionListener
{

    JMenuItem delete, newValue, findDN, makeNaming, removeNaming;  // displayable menu options for user input

    JXplorer jx;

    JTable table;                      // the table displaying the data - NOT CURRENTLY USED

    AttributeTableModel model;         // the data model - used to insert values into

    String attributeName = null;       // the currently selected attribute class name

    int currentRow;                    // the currently selected row.

    AttributeValue currentValue;       // type of currently selected table row

    AttributeType currentType;         // value of currently selected table row

    DN currentDN = null;               // used by the cache system?
//RDN currentRDN = null;             // used for naming attribute magic.

    AttributeValueCellEditor cellEditor = null;     //TE: to stop cell editing.

    private static Logger log = Logger.getLogger(SmartPopupTableTool.class.getName());

    /**
     * Constructor initialises the drop down menu and menu items, and registers 'this' component as being the listener
     * for all the menu items.
     */
    public SmartPopupTableTool(JTable t, AttributeTableModel m, JXplorer jxplorer)
    {
        jx = jxplorer;
        table = t;
        model = m;

        add(newValue = new JMenuItem(CBIntText.get("Add Another Value")));
        add(delete = new JMenuItem(CBIntText.get("Delete Value")));
        add(makeNaming = new JMenuItem(CBIntText.get("Make Naming Value")));
        add(removeNaming = new JMenuItem(CBIntText.get("Remove Naming Value")));
        add(new JSeparator());
        add(findDN = new JMenuItem(CBIntText.get("Find DN")));

        removeNaming.setVisible(false);

        findDN.addActionListener(this);
        newValue.addActionListener(this);
        delete.addActionListener(this);
        makeNaming.addActionListener(this);
        removeNaming.addActionListener(this);

        setVisible(false);
    }

    /**
     * Set the name of the attribute being operated with. That is, for new Value creation.
     */
    public void registerCurrentRow(AttributeType type, AttributeValue value, int row, RDN currentRDN)
    {
        currentType = type;
        currentValue = value;
        currentRow = row;

        if (currentType.toString().equalsIgnoreCase("objectclass"))
        {
            newValue.setEnabled(false);
            delete.setEnabled(false);
        }
        else
        {
            newValue.setEnabled(true);
            delete.setEnabled(true);
        }

        if (value.isNaming())
        {

            if (currentRDN != null)  // which it never should
            {
                if (currentRDN.size() > 1)
                    removeNaming.setVisible(true);
                else
                    removeNaming.setVisible(false);
            }
            makeNaming.setVisible(false);
        }
        else
        {
            if (currentRDN != null)
            {
                if (currentRDN.toString().indexOf(type.toString() + "=") > -1) // i.e. if we already have a naming att of this type...
                    makeNaming.setVisible(false);   // don't let the user add another one.
                else if (currentType.isMandatory())
                    makeNaming.setVisible(true);
                else
                    makeNaming.setVisible(false);
            }
            removeNaming.setVisible(false);
        }

    }

    /**
     * This handles the menu item actions.  They rely on the attributeName String being set prior to this method being
     * called (usually by setAttributeName() above).  Most of the action handling is simply tossing arguments to
     * JTable,
     * @param ev the active event, i.e. the menu item selected
     */
    public void actionPerformed(ActionEvent ev)
    {
        setVisible(false);

        Object eventSource = ev.getSource();
        if (eventSource == newValue)
        {
            cellEditor.stopCellEditing();   //TE: bug fix 3107
            newValue();
        }
        else if (eventSource == delete)
        {
            delete();
        }
        else if (eventSource == removeNaming)
        {
            removeRDNComponent();
        }
        else if (eventSource == makeNaming)
        {
            addRDNComponent();
        }
        else if (eventSource == findDN)
        {
            findDNComponent();
        }
        else  // should never happen...
        {
            log.log(Level.WARNING, "Unknown event in popup menu:\n", ev);
        }

        repaint();
    }

    /**
     * Performs a search on the attribute value.  If the value is a DN, the search result is displayed
     * in the Search Results tab.
     */
    public void findDNComponent()
    {
        if ("".equals(currentValue.getStringValue()))
        {
            jx.getSearchTree().clearTree();
            jx.getTreeTabPane().setSelectedComponent(jx.getResultsPanel());
            return;
        }

        String filter = "(objectclass=*)";
        DN dn = new DN(currentValue.getStringValue());

        String aliasOption = "always";
        log.info("Setting search alias option to: [" + aliasOption + "]");
        JXplorer.setProperty("option.ldap.searchAliasBehaviour", aliasOption);

        jx.getSearchBroker().setGUIQuiet(true);
        SearchExecute.run(jx.getSearchTree(), dn, filter, new String[]{"objectClass"}, 0, jx.getSearchBroker());

        jx.getTreeTabPane().setSelectedComponent(jx.getResultsPanel());
    }

    /**
     *
     */
    public void newValue()
    {
        int type = currentType.isMandatory() ? AttributeType.MANDATORY : AttributeType.NORMAL;
        String attName = currentType.getValue();
        AttributeValue newVal;
        if (currentValue.isBinary())
        {
            newVal = new AttributeValue(attName, null);
            newVal.setBinary(true);
        }
        else
            newVal = new AttributeValue(attName, "");

        model.addAttribute(attName, newVal, type, currentRow + 1);
        model.fireChange();
    }

    /**
     *
     */
    public void delete()
    {
        model.deleteAttribute(currentType.getValue(), currentRow);
        if (currentValue.isBinary())
            currentValue.setValue(null);
        model.fireChange();

        if ((currentType.getValue()).equalsIgnoreCase("jpegPhoto"))    //TE: deletes the temporary files associated with the current entry.
            CBCache.cleanCache(currentDN.toString());
    }

    /**
     *
     */
    public void removeRDNComponent()
    {
        if (model.getRDNSize() == 1)
            CBUtility.error(CBIntText.get("Cannot remove the last naming component!"));
        else
            model.removeNamingComponent(currentType, currentValue);
    }

    /**
     *
     */
    public void addRDNComponent()
    {
        if (currentValue.isBinary())
            CBUtility.error(CBIntText.get("Binary naming components are not supported."));
        else if (currentValue.isEmpty())
            CBUtility.error(CBIntText.get("A Naming Component must have an actual value."));
        else
            model.addNamingComponent(currentType, currentValue);
    }

    /**
     *
     * @param dn
     */
    public void setDN(DN dn)
    {
        currentDN = dn;
        //currentRDN = dn.getLowestRDN();
    }

    /**
     * registers the cell editor.  TE: for bug fix 3107.
     * @param myEditor the cell editor.
     */
    public void registerCellEditor(AttributeValueCellEditor myEditor)
    {
        cellEditor = myEditor;
    }
}