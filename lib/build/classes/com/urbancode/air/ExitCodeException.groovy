/*
* Licensed Materials - Property of IBM* and/or HCL**
* UrbanCode Deploy
* UrbanCode Build
* UrbanCode Release
* AnthillPro
* (c) Copyright IBM Corporation 2011, 2017. All Rights Reserved.
* (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*
* * Trademark of International Business Machines
* ** Trademark of HCL Technologies Limited
*/
package com.urbancode.air

class ExitCodeException extends Exception {
    /**
    * Construct a new ExitCodeException.
    */
   public ExitCodeException() {
       super();
   }

   /**
    * Construct a new ExitCodeException with the provided message.
    *
    * @param message A brief description of this exception.
    */
   public ExitCodeException(String message) {
       super(message);
   }

   /**
    * Construct a new ExitCodeException instance with the provided cause.
    *
    * @param cause The underlying cause of this exception.
    */
   public ExitCodeException(Throwable cause) {
       super(cause);
   }

   /**
    * Construct a new ExitCodeException instance with the provided message and cause.
    *
    * @param message A brief description of the exception.
    * @param cause The underlying exception which caused this exception to be emitted.
    */
   public ExitCodeException(String message, Throwable cause) {
       super(message, cause);
   }
}