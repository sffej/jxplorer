package com.ca.directory.jxplorer.viewer.tableviewer;

import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import javax.naming.*;
import javax.naming.directory.*;

import com.ca.commons.naming.*;

public class AttributeTableModel extends AbstractTableModel
{

    protected boolean dataChanged = false; // whether the user has modified the data.

    int noRows = 0;

    DXEntry oldEntry;                        // the original, unedited data

    Vector attributeTypes  = new Vector();   // vector of string IDs
    Vector attributeValues = new Vector();   // vector of attributeValues

    /**
     *    The number of naming values (e.g. the length of the
     *    namingTypes/namingRawValues vectors).
     */

    int numberNamingValues;


    /**
     *    <p>The ordered list of naming types (e.g. ['cn', 'id']) </p>
     *    <p>The namingTypes and namingRawValues arrays are set from
     *    the RDN.  They are then used to identify the corresponding
     *    naming AttributeValues, which are used to recreate the RDN
     *    later (as required).</p>
     *    <p>So the logic goes RDN -> string arrays -> array of naming
     *    attributes -> RDN
     */

    String[] namingTypes;

    /**
     *    The ordered list of naming values (e.g. ['Joe Bloggs', '7670001'])
     */

    String[] namingRawValues;

    /**
     *    Returns the size of the naming RDN.
     */

    public int getRDNSize()
    {
        return numberNamingValues;
    }

    /**
     *    Returns the class of the objects in the different columns:
     *    AttributeType.class for col 0, AttributeValue.class for col 1.
     */

    public Class getColumnClass(int c)
    {
        return (c==1)?AttributeValue.class: AttributeType.class;
    }

    /**
     *    Returns number of columns (=2)
     */
    public int getColumnCount() { return 2; }

    /**
     *    Returns number of rows (varies dynamically)
     */
    public int getRowCount() { return noRows;}

    /**
     *    Allows a cell value to be set (should only ever be the
     *    second column, col=1).
     */
    public void setValueAt(Object aValue,int row,int col)
    {
        dataChanged = true;

        if (col == 1) // which it jolly well should...
        {
            if (aValue instanceof AttributeValue)  // replace an attributeValues
            {
                attributeValues.set(row, aValue);
            }
            else                                    // ... or update it.
            {
                ((AttributeValue)attributeValues.elementAt(row)).update(aValue);
            }
            fireTableCellUpdated(row, col);
        }
    }

    /**
     *    Returns a value as a string.  Null and zero length string are
     *    made 1 character blank strings, so that the joke called swing
     *    printing can be kludged to work.
     */

    public String getStringValueAt(int row, int col)
    {
        String s = getValueAt(row,col).toString();
        if ((s == null) || (s.length()==0))
            s = " ";
        return s;
    }

    /**
     *    Return the object at a given position.
     */
    public Object getValueAt(int row, int col)
    {
        return (col==0)?attributeTypes.elementAt(row):attributeValues.elementAt(row);
    }

    /**
     *    Whether a particular column is editable (yes if col!=0)
     */
    public boolean isCellEditable(int row, int col)
    {
        if (col == 0) return false;  // can't edit attribute names
        if (col > 1) return false;   // should never happen.
        if (attributeTypes.elementAt(row).toString().equalsIgnoreCase("objectclass"))
            return false;            // can't edit object classes (this way)

        return true;
    }

    /**
     *    Gets column header names.
     */
    public String getColumnName(int columnIndex)
    {
        return (columnIndex == 0)?("attribute type"):("value");
    }

    /**
     *    'Resets' the value of all attribute Value cells back to their original value.
     */

    public void reset()
    {
        dataChanged = false;

        /*
         *    Return each attribute to the original value.
         */

        for (int i=0; i<attributeValues.size(); i++)
            ((AttributeValue)attributeValues.elementAt(i)).reset();

        /*
         *    Return the naming attribute list to the original
         *    (if necessary)
         */

        RDN rdn = oldEntry.getRDN();

        if (rdn.equals(getRDN()) == false)
            setRDN(rdn);

        /*
         *    All done - and the data has probably changed!
         */

        fireChange();
    }


    /**
     *    Removes all data leaving an empty table.
     */
    public void clear()
    {
        dataChanged = false;  // well, it sorta has I guess, but this is only done when
                              // we're starting from scratch.

        noRows = 0;
        attributeTypes.clear();
        attributeValues.clear();
        fireChange();

    }

    /**
     *    Insert a set of attributes into the table.
     */

    public void insertAttributes(DXEntry entry)
    {

        /*
         *    Check that we've got data to edit.
         */

        if (entry==null) return;

        /*
         *    Clear the decks for the new entry.
         */

        oldEntry = entry;
        noRows = 0;
        attributeTypes.clear();
        attributeValues.clear();

        /*
         *    work through the attributes, adding each attribute to the
         *    table model.
         */

        try
        {
            /*
             *    First, figure out the naming attibutes.  setRDN sets
             *    the string array of naming types/values.  Once set, these
             *    are used by insertAttribute to mark them as special values.
             */

            RDN rdn = entry.getRDN();
            setRDN(rdn);

            /*
             *    First, get the mandatory attributes which every entry 'MUST' contain.
             */

            DXNamingEnumeration mandatory = (DXNamingEnumeration)entry.getMandatory();

            while (mandatory.hasMore())
                insertAttribute(((DXAttribute)mandatory.next()), AttributeType.MANDATORY);

            /*
             *    Then add the 'MAY' contain optional attributes.
             */

            DXNamingEnumeration active = (DXNamingEnumeration)entry.getAllNonNull();
            active.sort();

            while (active.hasMore())
            {
                DXAttribute temp = (DXAttribute) active.next();

                if (mandatory.contains(temp)==false && (temp.size()>0) && (temp.get() != null))
                {
                    temp.sort();
                    insertAttribute(temp, AttributeType.NORMAL);
                }
            }

            // finally add attributes without values (i.e. ones that the user could add)
            // XXX need special code for null Binary attributes...

            DXNamingEnumeration possible = (DXNamingEnumeration)entry.getAll();
            possible.sort();

            while (possible.hasMore())
            {
                DXAttribute temp = (DXAttribute) possible.next();
                if (mandatory.contains(temp)==false && ((temp.size()==0) || (temp.get() == null)))
                {
                    insertAttribute(temp, AttributeType.NORMAL);
                }
            }

            fireChange();

            // This particular action we ignore, since it is the one that initially
            // sets up the table.
            dataChanged = false;
        }
        catch (NamingException e)
        {
            System.err.println("Naming Exception in AttributeTableModel: " + e);
        }
        catch (Exception e2)
        {
            System.err.println("Unexpected Exception in AttributeTableModel: " + e2);
            e2.printStackTrace();
        }

    }

    /**
     *    Sets up the list of naming attribute types - values pairs.
     *    (usually there is only ONE naming value, but sometimes an
     *    entry is multi valued).
     *    @param rdn the RDN to extract the naming information from.
     */

    protected void setRDN(RDN rdn)
    {
        numberNamingValues = rdn.size();  // almost always 1, but sometimes...
        namingTypes = new String[numberNamingValues];
        namingRawValues = new String[numberNamingValues];

        for (int i=0; i<numberNamingValues; i++)
        {
            namingTypes[i] = rdn.getAtt(i);
            namingRawValues[i] = rdn.getRawVal(i);
        }
    }



    /**
     *    Adds a single attribute, and (possibly multiple, or blank) values
     *    to the table.
     */

    public void insertAttribute(DXAttribute att, int type)
        throws NamingException
    {
        //boolean addingNamingValue = false;
        String namingValue = null;  // the exact naming value for this attribute (if any)

        String ID = att.getID();
        NamingEnumeration values = att.getAll();
        //shiba modified to enable list selection
        AttributeValue newAV = new AttributeValue(ID, ""); ;

        /*
         *    Add the Attribute to the internal data vectors.
         */

        if (att.size() == 0) // the attribute has no data (although the user may enter some later)
        {
            newAV = new AttributeValue(ID, "");
            if (att.isString() == false)
            {
                newAV.setBinary(true);
            }
            addAttribute(ID, newAV, type);
        }
        else //     The attribute already has one or more data values.
        {
            /*
             *    Check if we're adding a naming value - returns null if not...
             */

            namingValue = getAnyNamingValue(ID);
            // XXX If we want to support binary naming attributes, we would need to do some
            // nifty base-64 encoding comparison stuff here...
            if (namingValue != null && att.isString() == false)
                throw new NamingException("Binary naming attributes not supported in JXplorer: can't use attribute " + ID + " to name an entry");

            while (values.hasMore())
            {

                newAV = new AttributeValue(ID, values.next());

                /*
                 *    Set a Binary Attribute
                 */

                if (att.isString()==false)
                {
                    newAV.setBinary(true);
                }

                /*
                 *    Checks (and possibly Flags) that an AttributeValue
                 *    is a Naming Value.
                 */

                if (namingValue != null && newAV.getStringValue().equalsIgnoreCase(namingValue))
                        newAV.setNamingStatus(true);

                /*
                 *    Adds the attribute to the table.
                 */

                addAttribute(ID, newAV, type);
            }
        }
        //shiba modified to enable list selection
        if (att instanceof DXAttribute)
        {
            if (((DXAttribute)att).hasOptions())
                newAV.setOptions(((DXAttribute)att).getOptions());
            newAV.setBinary(!((DXAttribute)att).isString());
        }
    }


    /**
     * Determines whether a particular attribute ID is part of an RDN Attribute Value Assertion (AVA),
     * and if so, returnes the attribute value (for visual tagging in the display later on).
     *
     * @param ID the attribute ID to be tested, e.g. "cn"
     * @return the value (if any) corresponding to this i.d. - eg. "smith" if the rdn was "cn=smith" and "cn" was passed in.
     */
    private String getAnyNamingValue(String ID)
    {
        for (int i=0; i<numberNamingValues; i++)
            if (ID.equalsIgnoreCase(namingTypes[i]))
                return namingRawValues[i];

        return null;  // no value found.
    }



    public void addAttribute(String ID, AttributeValue val, int type)
    {
        attributeTypes.add(new AttributeType(ID, (type==AttributeType.MANDATORY)));
        attributeValues.add(val);
        noRows++;
    }

    public void addAttribute(String ID, AttributeValue val, int type, int indexPos)
    {
        attributeTypes.add(indexPos, new AttributeType(ID, (type==AttributeType.MANDATORY)));
        attributeValues.add(indexPos, val);
        noRows++;
    }

    public void deleteAttribute(String ID, int indexPos)
    {
        if (attributeTypes.elementAt(indexPos).toString().equals(ID))
        {
            ((AttributeValue)attributeValues.elementAt(indexPos)).update(new String(""));
        }
        else
            System.err.println("Internal error: attempt to delete attribute with invalid ID in AttributeTableModel"+
                               "\n att name = " + attributeTypes.elementAt(indexPos).toString() + " ID = " + ID);
    }

    public void fireChange()
    {
        dataChanged = true;

        fireTableChanged(new TableModelEvent(this));
    }

    public DXEntry getOldEntry()
    {
        return oldEntry;
    }

    /**
     *  <p>Returns the escaped RDN (possibly multi-valued).</p>
     *
     *  <p>Implementation Note: The returned RDN is created based on the internal naming
     *  type/value arrays, which must be synched with the user modified
     *  attribute list</p>
     */

    public RDN getRDN()
    {
        String rdn = "";
        for (int i=0; i<attributeValues.size(); i++)
        {
            AttributeValue entryValue = (AttributeValue)attributeValues.get(i);
            if (entryValue.isNaming())
            {
                if (rdn.length()>0)
                    rdn += "+";
                rdn += attributeTypes.get(i).toString() + "=" + NameUtility.escape(entryValue.getStringValue());
            }
        }

        // in some ultra-wierd cases, the naming attribute can be made invisible due to access controls.  While
        // you might think anyone who does this gets what they deserve, we try to recover by using the 'old entry'
        // DN.

        if ("".equals(rdn))
            rdn = oldEntry.getRDN().toString();


        return new RDN(rdn);
/*
        StringBuffer rdn = new StringBuffer();
        for (int i=0; i<numberNamingValues; i++)  // usually only 1...
        {
            if (i > 0)
                rdn.append('+');

            rdn.append(namingTypes[i]);
            rdn.append('=');
            rdn.append(NameUtility.escape(namingRawValues[i]));
        }
        return new RDN(rdn.toString());
*/
    }

    /**
     *    Returns a new entry
     */

    public DXEntry getNewEntry()
    {
        /*
         *    Sort out the name of the entry, based on the (possibly)
         *    edited attribute value fields.
         */

        DN newDN = new DN(oldEntry.getDN());


        RDN newRDN = getRDN();



        newDN.setRDN(newRDN, newDN.size()-1);

        /*
         *    Create an empty DXEntry object, initialised with the new name.
         */

        DXEntry newEntry = new DXEntry(newDN);

        /*
         *    Work through all the known attributes, adding the atts to the
         *    new entry.
         */

        AttributeValue test;
        String id;

        for (int i=0; i<attributeTypes.size(); i++)
        {
            test = (AttributeValue)attributeValues.elementAt(i);
            if (!test.isEmpty())
            {
                id = attributeTypes.elementAt(i).toString();
                BasicAttribute exists = (BasicAttribute)newEntry.get(id);
                if (exists == null)  // no values of this att. already registered
                    newEntry.put(new BasicAttribute(id, test.value()));
                else
                    exists.add(test.value());
            }
        }

        if (oldEntry.getStatus() == DXEntry.NEW)    // old entry isn't in directory yet.
        {
            newEntry.setStatus(DXEntry.NEW);
        }

        return newEntry;
    }

    /**
     *    Reads all the values for a given attribute
     *    from the table.
     */
    public Attribute getAttribute(String ID)
    {
        BasicAttribute returnAtt = new BasicAttribute(ID);
        for (int i=0; i<attributeTypes.size(); i++)
            if (ID.equals(attributeTypes.elementAt(i).toString()))
            {
                Object o = ((AttributeValue)attributeValues.elementAt(i)).value();

                // don't allow zero length string attributes...
                if (o!= null && o instanceof String)
                {
                    if (((String)o).length() == 0)
                        o = null;
                }

                if (o != null) // only add if there is a real value...
                    returnAtt.add(o);

            }

        return returnAtt;
    }

    /**
     *    Brute force search to find an attributeType given only the name.
     *    rarely used - main use is when popupTool tries to create a new
     *    attribute value entry, knowing only the type name.
     */

    public boolean isMandatory(String attributeTypeName)
    {
        for ( int i=0; i<attributeTypes.size(); i++)
        {
            if (((AttributeType)attributeTypes.elementAt(i)).toString().equals(attributeTypeName))
            {
                return ((AttributeType)attributeTypes.elementAt(i)).isMandatory();
            }
        }
        System.err.println("unable to find type name " + attributeTypeName);
        return false;  // couldn't find it.
    }

    /**
     *    Checks that all mandatory attributes have at least one value entered.
     */

    // surprisingly messy ftn.
    // Check through type list...
    //    find new type
    //    check if mandatory
    //    if mandatory, check values
    //       -> if no values found, return false
    //    continue until no types left.

    public boolean checkMandatoryAttributesSet()
    {
        AttributeType type, testType;
        AttributeValue value;
        String ID = "";
        boolean inDoubt = false;
        int i=0;

        while (i<noRows)                                            // for all rows
        {
            type = (AttributeType)attributeTypes.elementAt(i);

            if (type.isMandatory())                                // find mandatory types
            {
                ID = type.toString();
                inDoubt = true;
                testType = type;

                while (ID.equals(testType.toString()) && i<noRows) // cycle through all the values for that
                {                                                  // mandatory attribute...
                    if (inDoubt)                                   // ... until we find a valid value
                    {
                        value = (AttributeValue)attributeValues.elementAt(i);
                        if (value.isEmpty()==false)                // !!! found a valid value
                            inDoubt = false;
                    }
                    i++;
                    if (i<noRows) testType = (AttributeType)attributeTypes.elementAt(i);
                }

                if (inDoubt)         // Iff still in doubt, means no valid value was found
                    return false;    // *** RETURN FALSE ***   - mandatory value not filled in!
            }
            else
            {
                i++;
            }
        }

        return true;                 // *** RETURN TRUE *** - no unfilled out mandatory value found.
    }

    /**
     *    This removes a component from the array of naming atts, and
     *    sets the currentValue object to be a naming value.
     */

    public void removeNamingComponent(AttributeType currentType, AttributeValue currentValue)
    {

        try
        {
            String type = currentType.getValue();
            String value = currentValue.getStringValue();

            if ("".equals(type) || "".equals(value)) // which it really, really shouldn't...
                return;
            if (numberNamingValues == 1) // it would be a bad idea to remove the last naming value
                return;

            dataChanged = true;

            for (int i=0; i<numberNamingValues; i++)
            {
                if (type.equals(namingTypes[i]) && value.equals(namingRawValues[i]))
                {
                    int removeRow = i;
                    namingTypes = removeRowFromArray(namingTypes, removeRow);
                    namingRawValues = removeRowFromArray(namingRawValues, removeRow);
                    numberNamingValues--;
                    break;
                }
            }

            for (int i=0; i<attributeValues.size(); i++)
            {
                AttributeValue attval = (AttributeValue)attributeValues.get(i);
                if (attval.getID().equals(type))
                    if (attval.getStringValue().equals(value))
                    {
                        attval.setNamingStatus(false);
                    }
            }
        }
        catch (Exception e) // nope, we won't be doing that.
        {
            e.printStackTrace();
            return;
        }

    }


    protected static String[] removeRowFromArray(String[] array, int removeRow)
    {
        int originalLength = array.length;
        if (removeRow < 0 || removeRow >= originalLength)
            return array;

        String[] temp = new String[array.length-1];

        if (removeRow > 0)
            System.arraycopy(array, 0, temp, 0, removeRow);

        if (removeRow < originalLength-1)
            System.arraycopy(array, removeRow+1, temp, removeRow, (originalLength-removeRow-1));

        return temp;
    }


    public void dumpNamingArrays()
    {
        System.out.println("dump naming array");
        for (int i=0; i<numberNamingValues; i++)
        {
            System.out.println(i + " type        " + namingTypes[i]);
            System.out.println(i + " value       " + namingRawValues[i]);
        }
    }


    public void addNamingComponent(AttributeType currentType, AttributeValue currentValue)
    {

        String type = currentType.getValue();
        String value = currentValue.getStringValue();

        if ("".equals(type) || "".equals(value)) // which it really, really shouldn't...
            return;

        dataChanged = true;

        String[] tempTypes = new String[numberNamingValues+1];
        String[] tempRawValues = new String[numberNamingValues+1];

        System.arraycopy(namingTypes, 0, tempTypes, 0, numberNamingValues);
        System.arraycopy(namingRawValues, 0, tempRawValues, 0, numberNamingValues);

        tempTypes[numberNamingValues] = type;
        tempRawValues[numberNamingValues] = value;

        numberNamingValues++;

        namingTypes = tempTypes;
        namingRawValues = tempRawValues;

        currentValue.setNamingStatus(true);
    }



    /**
     *    This returns whether the table data has been modified
     *    since the original display of the entry.
     */

    public boolean changedByUser()
    {
        return dataChanged;
    }
}