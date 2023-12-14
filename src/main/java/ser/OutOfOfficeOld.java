package ser;

import com.ser.blueline.IUser;
import com.ser.blueline.bpm.*;
import com.ser.blueline.metaDataComponents.IStringMatrix;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OutOfOfficeOld extends UnifiedAgent {
    private Logger log = LogManager.getLogger();

    @Override
    protected Object execute() {
        if (getEventTask() == null) return resultError("OBJECT CLIENT ID is NULL or not of Type ITask");
        try {
            log.info("----Delegation Agent Started -----");

            IUser currentUser = getSes().getUser();
            ITask mainTask = getEventTask();
            IProcessInstance processInstance = getEventTask().getProcessInstance();
            IUser processOwner = processInstance.getOwner();

            if(getEventTask().getProcessInstance().findLockInfo().getOwnerID() != null){
                log.error("Task is locked.." + getEventTask().getID() + "..restarting agent");
                return resultRestart("Restarting Agent");
            }
            String dlgStart = mainTask.getDescriptorValue("orgUserDelegationStart");
            String dlgFinish = mainTask.getDescriptorValue("orgUserDelegationUntil");
            Date dtStart = formulateDateFromStr(dlgStart);
            Date dtFinish = formulateDateFromStr(dlgFinish);

            String userName = mainTask.getDescriptorValue("orgUserDelegationFrom");
            /*if(userName == null){
                mainTask.setDescriptorValue("orgUserDelegationFrom",processOwner.getLogin());
                mainTask.commit();
                userName = mainTask.getDescriptorValue("orgUserDelegationFrom");
            }*/
            //IUser emplUser = getDocumentServer().getUserByLoginName(getSes(),userName);
            IUser emplUser = getDocumentServer().getUserByLoginName(getSes(),processOwner.getLogin());
            if(emplUser == null)return resultError("UserName is NULL");
            String userWBName = getWorkbasketIDfromUserID(emplUser.getID());
            if (userWBName==null)return resultError("Workbasket Name is NULL");
            IWorkbasket userWB = getBpm().getWorkbasketByName(userWBName);
            IReceivers delegatedUsers = userWB.getActionOnAssignReceivers();

            String dUserWBID = mainTask.getDescriptorValue("orgUserDelegationTo");
            if(dUserWBID == null){
                //IReceivers receivers = userWB.getActionOnAssignReceivers();
            }else {
                IWorkbasket dUserWB = getBpm().getWorkbasket(dUserWBID);
                IWorkbasket userWBCopy = userWB.getModifiableCopy(getSes());
                userWBCopy.setActionOnAssign(WorkbasketActionOnAssign.DELEGATE);
                userWBCopy.setAbsenceInfo("Delegation TEST Information....");
                userWBCopy.setAbsenceSince(dtStart);
                userWBCopy.setAbsenceUntil(dtFinish);
                IReceivers receivers = getBpm().createReceivers(dUserWB);
                userWBCopy.setActionOnAssignReceivers(receivers);
                userWBCopy.commit();
                log.info("Delegated from:" + processOwner.getLogin() + " /// to (WB ID):" + dUserWBID);
            }
        } catch (Exception e) {
            log.error("Exception Caught");
            log.error(e.getMessage());
            return resultError(e.getMessage());
        }
        return resultSuccess("Agent Finished Succesfully");
    }
    public Date formulateDateFromStr(String dateStr){
        List<SimpleDateFormat> knownPatterns = new ArrayList<>();
        knownPatterns.add(new SimpleDateFormat("dd-MM-yyyy"));
        knownPatterns.add(new SimpleDateFormat("dd/MM/yyyy"));
        knownPatterns.add(new SimpleDateFormat("dd.MM.yyyy"));
        knownPatterns.add(new SimpleDateFormat("yyMMdd"));
        knownPatterns.add(new SimpleDateFormat("yyyyMMdd"));
        knownPatterns.add(new SimpleDateFormat("ddMMyyyy"));
        knownPatterns.add(new SimpleDateFormat("MMddyyyy"));
        for (SimpleDateFormat pattern : knownPatterns) {
            try {
                // Take a try
                pattern.setLenient(false);
                return pattern.parse(dateStr);
            } catch (java.text.ParseException pe) {
                // Loop on
            }
        }
        return null;
    }
    public String getWorkbasketIDfromUserID(String userID) throws Exception {
        log.info("Getting workbasket ID from user ID");
        IStringMatrix workbaskets = getDocumentServer().getStringMatrixByID("Workbaskets", getSes());
        if (workbaskets == null) throw new Exception("Workbaskets Global Value List not found");
        List<List<String>> rawTable = workbaskets.getRawRows();
        return getDatafromTable(userID,rawTable);
    }
    public String getDatafromTable(String userID, List<List<String>> rawTable) {
        for(List<String> list : rawTable) {
            if(list.contains(userID)) {
                //we found the user
                //return first column as workbasketID
                log.info("workbasket name for user ID: " + userID + " is " + list.get(1));
                return list.get(1);
            }
        }
        return null;
    }
}
