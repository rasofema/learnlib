/* Copyright (C) 2013-2023 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.exception;

// Thrown when a learner requests the answer to a query, however a query limit has been reached.
public class LimitException extends RuntimeException {

    /**
     * Default constructor.
     *
     * @see IllegalArgumentException#IllegalArgumentException()
     */
    public LimitException() {
        super();
    }

    /**
     * Constructor.
     *
     * @see IllegalArgumentException#IllegalArgumentException(String, Throwable)
     */
    public LimitException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor.
     *
     * @see IllegalArgumentException#IllegalArgumentException(String)
     */
    public LimitException(String s) {
        super(s);
    }

    /**
     * Constructor.
     *
     * @see IllegalArgumentException#IllegalArgumentException(Throwable)
     */
    public LimitException(Throwable cause) {
        super(cause);
    }

}