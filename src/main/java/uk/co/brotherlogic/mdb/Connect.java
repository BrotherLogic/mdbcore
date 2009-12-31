package uk.co.brotherlogic.mdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Class to deal with database connection
 * 
 * @author Simon Tucker
 */
public final class Connect
{
	/** enum of modes */
	private enum mode
	{
		DEVELOPMENT, PRODUCTION
	}

	/** Current mode of operation */
	private static mode operationMode = mode.DEVELOPMENT;

	/** The connection to the local DB */
	private Connection locDB;

	private static Connect singleton;

	private Connect(mode operationMode) throws SQLException
	{
		makeConnection(operationMode);
	}

	/**
	 * Cancels all impending transactions
	 * 
	 * @throws SQLException
	 *             if the cancel fails
	 */
	public void cancelTrans() throws SQLException
	{
		locDB.rollback();
	}

	/**
	 * Commits the impending transactions
	 * 
	 * @throws SQLException
	 *             If the commit fails
	 */
	public void commitTrans() throws SQLException
	{
		locDB.commit();
	}

	/**
	 * Builds a prepared statements from the data store
	 * 
	 * @param sql
	 *            The statement to build
	 * @return a {@link PreparedStatement}
	 * @throws SQLException
	 *             If the construction fails
	 */
	public PreparedStatement getPreparedStatement(final String sql) throws SQLException
	{
		// Create the statement
		PreparedStatement ps = locDB.prepareStatement(sql);

		return ps;
	}

	/**
	 * Makes the connection to the DB
	 * 
	 * @throws SQLException
	 *             if something fails
	 */
	private void makeConnection(mode operationMode) throws SQLException
	{
		try
		{
			// Load all the drivers and initialise the database connection
			Class.forName("org.postgresql.Driver");

			if (operationMode == mode.PRODUCTION)
			{
				System.err.println("Connecting to production database");
				locDB = DriverManager.getConnection("jdbc:postgresql://192.168.1.100/music?user=music");
			}
			else
			{
				System.err.println("Connection to development database");
				locDB = DriverManager.getConnection("jdbc:postgresql://localhost/music?user=music");
			}

			// Switch off auto commit
			locDB.setAutoCommit(false);
		}
		catch (ClassNotFoundException e)
		{
			throw new SQLException(e);
		}
	}

	/**
	 * Static constructor
	 * 
	 * @return A suitable db connection
	 * @throws SQLException
	 *             if a db connection cannot be established
	 */
	public static Connect getConnection() throws SQLException
	{
		if (singleton == null)
			singleton = new Connect(operationMode);
		return singleton;
	}

	public static void setForProduction()
	{
		operationMode = mode.PRODUCTION;
	}

}
