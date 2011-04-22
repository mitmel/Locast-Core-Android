package com.beoui.geocell;

import java.util.List;

import com.beoui.geocell.model.GeocellQuery;

public interface GeocellQueryEngine {

	public abstract <T> List<T> query(GeocellQuery baseQuery, List<String> curGeocellsUnique, Class<T> entityClass);

}