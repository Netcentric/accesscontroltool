package biz.netcentric.cq.tools.actool.aceservice.impl.model;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;

public class PathACL implements Comparable<PathACL> {
	private String path;
	private JackrabbitAccessControlList acl;
	

	/**
	 * Constructor.
	 * 
	 * @param path the path to which the ACL applies
	 * @param acl the ACL
	 */
	public PathACL(String path, JackrabbitAccessControlList acl) {
		super();
		this.path = path;
		this.acl = acl;
	}
	
	/**
	 * The path for the ACL.
	 * 
	 * @return the path
	 */
	public String getPath() {
		return path;
	}
	
	/**
	 * The ACL for the path.
	 * 
	 * @return the acl
	 */
	public JackrabbitAccessControlList getAcl() {
		return acl;
	}

	@Override
	public int compareTo(PathACL other) {
		
		return this.path.compareTo(other.path);
	}
	
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((acl == null) ? 0 : acl.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PathACL other = (PathACL) obj;
		if (acl == null) {
			if (other.acl != null)
				return false;
		} else if (!acl.equals(other.acl))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}
}
