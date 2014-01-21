package biz.netcentric.cq.tools.actool.installationhistory;


import java.sql.Timestamp;


public class HistoryEntry {
    
	private Timestamp timestamp;
	private String message;
	private long index;
	
	public HistoryEntry(long index, Timestamp timestamp, String message) {
		super();
		this.index = index;
		this.timestamp = timestamp;
		this.message = message;
	}

	

	public Timestamp getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Timestamp timestamp) {
		this.timestamp = timestamp;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getIndex() {
		return index;
	}

	public void setIndex(long index) {
		this.index = index;
	}
	
	
}
