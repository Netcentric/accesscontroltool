package biz.netcentric.cq.tools.actool.installationhistory;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.HistoryEntryComparator;

public class AcInstallationHistoryPojo {
	
	private static final Logger LOG = LoggerFactory.getLogger(AcInstallationHistoryPojo.class);
	
	private static final String MSG_IDENTIFIER_EXCEPTION = "EXCEPTION:";
	private static final String MSG_IDENTIFIER_WARNING = "WARNING:";
	
	
	private Set <HistoryEntry> warnings = new HashSet<HistoryEntry> ();
	private Set <HistoryEntry> messages =  new HashSet<HistoryEntry> ();
	private Set <HistoryEntry> exceptions =  new HashSet<HistoryEntry> ();
	
	private Set <HistoryEntry> verboseMessages =  new HashSet<HistoryEntry> ();

	private boolean success = true;
	private Date installationDate;
	private long executionTime;
	private long msgIndex = 0;
	Rendition rendition;
	
	public enum Rendition {
		 HTML, TXT; 
		}
	
	public AcInstallationHistoryPojo() {
		this.rendition = rendition.TXT;
		this.setInstallationDate(new Date());
	}
	public AcInstallationHistoryPojo(Rendition rendition) {
		this.rendition = rendition;
		this.setInstallationDate(new Date());
	}
	
	
	public Date getInstallationDate() {
		return installationDate;
	}

	public void setInstallationDate(final Date installationDate) {
		this.installationDate = installationDate;
	}

	public long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(final long time) {
		this.executionTime = time;
	}

	public  Set <HistoryEntry> getWarnings() {
		return warnings;
	}
	
	public void addWarning(String warning){
		if(this.rendition.equals(Rendition.HTML)){
		    this.warnings.add(new HistoryEntry(msgIndex, new Timestamp(new Date().getTime()), "<font color='orange'><b>" + MSG_IDENTIFIER_WARNING + " " + warning +"</b></font>"));
		}else if(this.rendition.equals(Rendition.TXT)){
			this.warnings.add(new HistoryEntry(msgIndex, new Timestamp(new Date().getTime()), MSG_IDENTIFIER_WARNING + " " + warning ));
		}
		msgIndex++;
	}
	public void addMessage(String message){
		this.messages.add(new HistoryEntry(msgIndex, new Timestamp(new Date().getTime()), " " + message));
		msgIndex++;
	}
	public void setException(final String exception) {
		if(this.rendition.equals(Rendition.HTML)){
		this.exceptions.add(new HistoryEntry(msgIndex, new Timestamp(new Date().getTime()), "<font color='red'><b>" + MSG_IDENTIFIER_EXCEPTION +"</b>"+ " " + exception+"</b></font>"));
		}else if(this.rendition.equals(Rendition.TXT)){
			this.exceptions.add(new HistoryEntry(msgIndex, new Timestamp(new Date().getTime()), MSG_IDENTIFIER_EXCEPTION + "</b>" + " " + exception));

		}
		this.success = false;
		msgIndex++;
	}
	public void addVerboseMessage(String message){
		this.verboseMessages.add(new HistoryEntry(msgIndex, new Timestamp(new Date().getTime()), " " + message));
		msgIndex++;
	}
	
	public  Set <HistoryEntry> getMessages(){
		return this.messages;
	}
	
	public Set <HistoryEntry> getException() {
		return this.exceptions;
	}
	
	public boolean isSuccess() {
		return success;
	}
	
	public void setSuccess(final boolean success) {
		this.success = success;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("\n" + "Installation triggered: " + this.installationDate.toString() + "\n");
		
		sb.append("\n" + getMessageHistory() + "\n");
		
		sb.append("\n" + "Execution time: " + this.executionTime + " ms\n");
		
        if(this.success){
        	sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_OPEN);
        }else{
        	sb.append(HtmlConstants.FONT_COLOR_NO_SUCCESS_HTML_OPEN);
        }
		sb.append("\n" + "Success: " + this.success);
		
        sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE);
		return sb.toString();
	}
	
	
	@SuppressWarnings("unchecked")
	public String getMessageHistory(){
		return getMessageString(getMessageSet(this.warnings, this.messages, this.exceptions));
	}
	
	@SuppressWarnings("unchecked")
	public String getVerboseMessageHistory(){
		return getMessageString(getMessageSet(this.warnings, this.messages, this.verboseMessages, this.exceptions));
	}
	
	private Set<HistoryEntry> getMessageSet(Set <HistoryEntry>...sets){
		Set <HistoryEntry> resultSet = new TreeSet<HistoryEntry>(new HistoryEntryComparator());
		
		for(Set<HistoryEntry> set : sets){
			for(HistoryEntry entry : set){
				resultSet.add(entry);
			}
		}
		return resultSet;
	}
	
	private String getMessageString(Set<HistoryEntry> messageHistorySet){
		StringBuilder sb = new StringBuilder();
		if(!messageHistorySet.isEmpty()){
			for(HistoryEntry entry : messageHistorySet){
				sb.append("\n" + entry.getTimestamp() + ": " + entry.getMessage());
			}
		}
		return sb.toString();
	}
}
