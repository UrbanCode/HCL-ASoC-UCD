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

import java.util.Map;


class Folder {
    def id
    def name
    def parentId
    def Map<String, Folder> children = new HashMap<String, Folder>()
    
    public Folder(id, name, parentId) {
        this.id = id
        this.name = name
        this.parentId = parentId
    }

    public boolean equals(Object o) {
        if (o instanceof Folder && o != null) {
            return id.equals(o.id)
        }
        else {
            return false
        }
    }

    public String toString() {
        return "[id:$id, name:$name, parent:$parentId, children:${children.keySet()}]"
    }
}

