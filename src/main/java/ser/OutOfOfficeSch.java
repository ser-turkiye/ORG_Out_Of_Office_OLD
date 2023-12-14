package ser;

import com.ser.blueline.IInformationObject;
import com.ser.blueline.IUser;
import com.ser.blueline.bpm.*;
import com.ser.blueline.metaDataComponents.IStringMatrix;
import com.spire.ms.System.DateTime;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class OutOfOfficeSch extends UnifiedAgent {
    private Logger log = LogManager.getLogger();
    private ProcessHelper helper;
    private ITask mainTask;
    @Override
    protected Object execute() {
        if (getBpm() == null) return resultError("OutOfOfficeSch...BPM is NULL");
        try {
            log.info("----Delegation Agent Started -----");
            this.helper = new ProcessHelper(getSes());
            Date now = getZeroTimeDate(new Date());
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
            LocalDateTime nowtime = LocalDateTime.now();

            IInformationObject[] delegationTasks = this.getDelegationTasks();
            for(IInformationObject dTask : delegationTasks){
                mainTask = (ITask) dTask;
                log.info("----Delegation Started for Task ID-----" + mainTask.getID());
                String prcs = mainTask.getDescriptorValue("orgProcessID");
                IProcessInstance processInstance = mainTask.getProcessInstance();
                IUser processOwner = processInstance.getOwner();

                if(mainTask.getProcessInstance().findLockInfo().getOwnerID() != null){
                    log.error("Task is locked.." + mainTask.getID() + "..restarting agent");
                    return resultRestart("Restarting Agent");
                }
                String dlgStart = mainTask.getDescriptorValue("orgUserDelegationStart");
                String dlgFinish = mainTask.getDescriptorValue("orgUserDelegationUntil");
                String wbIsShared = mainTask.getDescriptorValue("orgUserDelegationExistingTask");
                Date dtStart = getZeroTimeDate(formulateDateFromStr(dlgStart));
                Date dtFinish = getZeroTimeDate(formulateDateFromStr(dlgFinish));
                boolean isEqualStart = dtStart.equals(now); ///for delegation
                boolean isEqualFinish = dtFinish.equals(now); ///for remove delegation
                log.info("Delegation check start date: ("+ dlgStart +"):("+ isEqualStart +")");
                log.info("Delegation check finish date: ("+ dlgFinish +"):("+ isEqualFinish +")");
                if(dtStart.after(dtFinish) || dtStart.equals(dtFinish)){
                    log.info("Start Date ("+ dlgStart +") cannot be later than or equal Finish Date ("+ dlgFinish +") : " + mainTask.getID());
                    continue;
                }
                if(isEqualStart){
                    //if(prcs.contains("DISABLED")){continue;}
                    IUser emplUser = getDocumentServer().getUserByLoginName(getSes(),processOwner.getLogin());
                    if(emplUser == null)return resultError("UserName is NULL");
                    String userWBName = getWorkbasketIDfromUserID(emplUser.getID());
                    if (userWBName==null)return resultError("Workbasket Name is NULL");
                    IWorkbasket userWB = getBpm().getWorkbasketByName(userWBName);
                    IReceivers delegatedUsers = userWB.getActionOnAssignReceivers();

                    String dUserWBID = mainTask.getDescriptorValue("orgUserDelegationTo");
                    if(dUserWBID != null){
                        IWorkbasket dUserWB = getBpm().getWorkbasket(dUserWBID);
                        String dUserLogin = getUserIDfromWorkbasket(dUserWB.getID());
                        IUser dUser = getDocumentServer().getUserByLoginName(getSes(),dUserLogin);
                        IWorkbasket userWBCopy = userWB.getModifiableCopy(getSes());
                        userWBCopy.setActionOnAssign(WorkbasketActionOnAssign.DELEGATE);
                        userWBCopy.setAbsenceInfo("Delegation Information....");
                        userWBCopy.setAbsenceSince(dtStart);
                        userWBCopy.setAbsenceUntil(dtFinish);
                        IReceivers receivers = getBpm().createReceivers(dUserWB);
                        userWBCopy.setActionOnAssignReceivers(receivers);
                        if(Objects.equals(wbIsShared, "true")){
                            userWBCopy.addAccessibleBy(dUser);
                        }
                        userWBCopy.commit();
                        log.info("Delegated from:" + processOwner.getLogin() + " /// to:" + dUser.getLogin());
                        mainTask.setDescriptorValue("orgUserDelegationFrom",processOwner.getLogin());
                        mainTask.setDescriptorValue("orgProcessID", "DELEGATION-ENABLED-" + dtf.format(nowtime));
                        mainTask.commit();
                    }
                }
                if(isEqualFinish){
                    IUser emplUser = getDocumentServer().getUserByLoginName(getSes(),processOwner.getLogin());
                    if(emplUser == null)return resultError("UserName is NULL");
                    String userWBName = getWorkbasketIDfromUserID(emplUser.getID());
                    if (userWBName==null)return resultError("Workbasket Name is NULL");
                    IWorkbasket userWB = getBpm().getWorkbasketByName(userWBName);

                    String dUserWBID = mainTask.getDescriptorValue("orgUserDelegationTo");
                    if(dUserWBID != null){
                        IWorkbasket dUserWB = getBpm().getWorkbasket(dUserWBID);
                        String dUserLogin = getUserIDfromWorkbasket(dUserWB.getID());
                        IUser dUser = getDocumentServer().getUserByLoginName(getSes(),dUserLogin);
                        IWorkbasket userWBCopy = userWB.getModifiableCopy(getSes());
                        if(Objects.equals(wbIsShared, "true")){
                            userWBCopy.removeAccessibleBy(dUser);
                        }
                        userWBCopy.commit();
                        log.info("Delegation disabled from:" + processOwner.getLogin() + " /// to:" + dUser.getLogin());
                        mainTask.setDescriptorValue("orgUserDelegationFrom",processOwner.getLogin());
                        mainTask.setDescriptorValue("orgProcessID", "DELEGATION-DISABLED-" + dtf.format(nowtime));
                        mainTask.commit();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Exception Caught");
            log.error(e.getMessage());
            return resultError(e.getMessage());
        }
        return resultSuccess("Agent Finished Succesfully");
    }
    private static Date getZeroTimeDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        date = calendar.getTime();
        return date;
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
    public String getUserIDfromWorkbasket(String wbID) throws Exception {
        log.info("Getting User Login from workbasket");
        IStringMatrix workbaskets = getDocumentServer().getStringMatrixByID("Workbaskets", getSes());
        if (workbaskets == null) throw new Exception("Workbaskets Global Value List not found");
        List<List<String>> rawTable = workbaskets.getRawRows();
        return getDatafromTable(wbID,rawTable);
    }
    public String getDatafromTable(String key, List<List<String>> rawTable) {
        for(List<String> list : rawTable) {
            if(list.contains(key)) {
                //we found the user
                //return first column as workbasketID
                log.info("workbasket name for user ID: " + key + " is " + list.get(1));
                return list.get(1);
            }
        }
        return null;
    }
    private IInformationObject[] getDelegationTasks() throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.OutOfOfficeProcess).append("'");
        builder.append(" AND WFL_TASK_NAME = '").append(Conf.Tasks.OutOfOfficeProcessTask).append("'");
                //.append(" AND ").append("ORGPROCESSID").append("='NULL'");
        String whereClause = builder.toString();
        log.info("Where Clause: " + whereClause);
        IInformationObject[] informationObjects = helper.createQuery(new String[]{"BPM_SYS_ORG"} , whereClause , 2);
        //if(informationObjects.length < 1) throw new Exception("No Hits found for query: " + whereClause);
        //if(informationObjects.length > 1) throw new Exception("Multiple hits found for query: " + whereClause);
        return informationObjects;
    }
}
