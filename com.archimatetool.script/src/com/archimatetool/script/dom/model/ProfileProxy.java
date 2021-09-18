/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.script.dom.model;

import org.eclipse.osgi.util.NLS;

import com.archimatetool.editor.model.commands.RemoveListMemberCommand;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.IProfiles;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.archimatetool.script.ArchiScriptException;
import com.archimatetool.script.commands.CommandHandler;
import com.archimatetool.script.commands.ScriptCommandWrapper;
import com.archimatetool.script.commands.SetCommand;

/**
 * Proxy wrapper around an IProfile
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ProfileProxy implements Comparable<ProfileProxy> {
    
    private IProfile profile;

    ProfileProxy(IProfile profile) {
        this.profile = profile;
    }
    
    IProfile getProfile() {
        return profile;
    }
    
    public String getName() {
        return profile.getName();
    }
    
    public ProfileProxy setName(String name) {
        // Sames
        if(profile.getName().equals(name)) {
            return this;
        }
        
        if(!StringUtils.isSetAfterTrim(name)) {
            throw new ArchiScriptException("Specialization name must not be empty!");
        }
        
        // Check we don't already have one
        if(ArchimateModelUtils.hasProfileByNameAndType(profile.getArchimateModel(), name, profile.getConceptType())) {
            throw new ArchiScriptException(NLS.bind("The specialization ''{0}'' already exists!", name));
        }
        
        CommandHandler.executeCommand(new SetCommand(profile, IArchimatePackage.Literals.NAMEABLE__NAME, name));
        
        return this;
    }
    
    public String getType() {
        return ModelUtil.getKebabCase(profile.getConceptType());
    }
    
    public ProfileProxy setType(String conceptType) {
        // Convert kebab to camel case
        conceptType = ModelUtil.getCamelCase(conceptType);
        
        // Same
        if(profile.getConceptType().equals(conceptType)) {
            return this;
        }
        
        // Check it's the correct type
        if(!ModelUtil.isArchimateConcept(conceptType)) {
            throw new ArchiScriptException(NLS.bind(Messages.ModelFactory_11, conceptType));
        }
        
        // Check whether the profile is being used
        if(!ArchimateModelUtils.findProfileUsage(profile).isEmpty()) {
            throw new ArchiScriptException(NLS.bind("The specialization ''{0}'' is in use and the type can't be changed!", profile.getName()));
        }
        
        // Check we don't already have one
        if(ArchimateModelUtils.hasProfileByNameAndType(profile.getArchimateModel(), profile.getName(), conceptType)) {
            throw new ArchiScriptException(NLS.bind("The specialization ''{0}'' already exists!", profile.getName()));
        }
        
        CommandHandler.executeCommand(new SetCommand(profile, IArchimatePackage.Literals.PROFILE__CONCEPT_TYPE, conceptType));
        
        return this;
    }
    
    public Object getImage() {
        return profile.getImagePath();
    }
    
    public ProfileProxy setImage(String imagePath) {
        // If imagePath is not null check that the ArchiveManager has this image
        if(imagePath != null && !ModelUtil.hasImage(profile.getArchimateModel(), imagePath)) {
            throw new ArchiScriptException(NLS.bind(Messages.ModelFactory_12, imagePath));
        }

        CommandHandler.executeCommand(new SetCommand(profile, IArchimatePackage.Literals.DIAGRAM_MODEL_IMAGE_PROVIDER__IMAGE_PATH, imagePath));
        
        return this;
    }
    
    public void delete() {
        // Delete Usages first
        for(IProfiles owner : ArchimateModelUtils.findProfileUsage(getProfile())) {
            CommandHandler.executeCommand(new ScriptCommandWrapper(new RemoveListMemberCommand<IProfile>(owner.getProfiles(), profile), profile));
        }

        // Then delete the Profile from the Model
        CommandHandler.executeCommand(new ScriptCommandWrapper(new RemoveListMemberCommand<IProfile>(profile.getArchimateModel().getProfiles(), profile), profile));
    }
    
    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        
        if(!(obj instanceof ProfileProxy)) {
            return false;
        }
        
        if(getProfile() == null) {
            return false;
        }
        
        return getProfile() == ((ProfileProxy)obj).getProfile();
    }
    
    // Need to use the hashCode of the underlying object because a Java Set will use it for contains()
    @Override
    public int hashCode() {
        return getProfile() == null ? super.hashCode() : getProfile().hashCode();
    }
    
    @Override
    public String toString() {
        return getName() + ": " + getType();
    }

    @Override
    public int compareTo(ProfileProxy p) {
        if(p == null || p.getName() == null || getName() == null) {
            return 0;
        }
        return getName().compareTo(p.getName());
    }
}
