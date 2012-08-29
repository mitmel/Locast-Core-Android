package com.stackoverflow;

/**
 * License: http://creativecommons.org/licenses/by-sa/2.5/
 *
 * @author jon + Alan
 * @see http://stackoverflow.com/questions/122105/java-what-is-the-best-way-to-filter-a-collection
 */
public interface Predicate<T> {
    boolean apply(T in);
}
