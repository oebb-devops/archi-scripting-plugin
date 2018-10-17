/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.views.history;

import java.io.IOException;
import java.util.List;

import org.archicontribs.modelrepository.ModelRepositoryPlugin;
import org.archicontribs.modelrepository.actions.ExtractModelFromCommitAction;
import org.archicontribs.modelrepository.actions.ResetToRemoteCommitAction;
import org.archicontribs.modelrepository.actions.RestoreCommitAction;
import org.archicontribs.modelrepository.actions.UndoLastCommitAction;
import org.archicontribs.modelrepository.grafico.ArchiRepository;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IArchiRepository;
import org.archicontribs.modelrepository.grafico.IRepositoryListener;
import org.archicontribs.modelrepository.grafico.RepositoryListenerManager;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.ui.components.UpdatingTableColumnLayout;
import com.archimatetool.model.IArchimateModel;


/**
 * History Viewpart
 */
public class HistoryView
extends ViewPart
implements IContextProvider, ISelectionListener, IRepositoryListener {

	public static String ID = ModelRepositoryPlugin.PLUGIN_ID + ".historyView"; //$NON-NLS-1$
    public static String HELP_ID = ModelRepositoryPlugin.PLUGIN_ID + ".modelRepositoryViewHelp"; //$NON-NLS-1$
    
    private HistoryTableViewer fHistoryTableViewer;
    private Label fRepoLabel;
    private RevisionCommentViewer fCommentViewer;
    
    private ComboViewer fBranchesComboViewer;
    
    /*
     * Actions
     */
    private ExtractModelFromCommitAction fActionExtractCommit;
    private RestoreCommitAction fActionRestoreCommit;
    private UndoLastCommitAction fActionUndoLastCommit;
    private ResetToRemoteCommitAction fActionResetToRemoteCommit;
    
    
    /*
     * Selected repository
     */
    private IArchiRepository fSelectedRepository;

    
    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout());
        
        // Create Info Section
        createInfoSection(parent);
        
        // Create History Table and Comment Viewer
        createHistorySection(parent);

        makeActions();
        hookContextMenu();
        //makeLocalMenuActions();
        makeLocalToolBarActions();
        
        // Register us as a selection provider so that Actions can pick us up
        getSite().setSelectionProvider(getHistoryViewer());
        
        // Listen to workbench selections
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);

        // Register Help Context
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getHistoryViewer().getControl(), HELP_ID);
        
        // Initialise with whatever is selected in the workbench
        IWorkbenchPart part = getSite().getWorkbenchWindow().getPartService().getActivePart();
        if(part != null) {
            selectionChanged(part, getSite().getWorkbenchWindow().getSelectionService().getSelection());
        }
        
        // Add listener
        RepositoryListenerManager.INSTANCE.addListener(this);
    }
    
    private void createInfoSection(Composite parent) {
        Composite mainComp = new Composite(parent, SWT.NONE);
        mainComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        mainComp.setLayout(layout);

        // Repository name
        fRepoLabel = new Label(mainComp, SWT.NONE);
        fRepoLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fRepoLabel.setText(Messages.HistoryView_0);
        
        // Branches
        Label label = new Label(mainComp, SWT.NONE);
        label.setText("Branch:");

        fBranchesComboViewer = new ComboViewer(mainComp, SWT.READ_ONLY);
        GridData gd = new GridData();
        fBranchesComboViewer.getCombo().setLayoutData(gd);

        fBranchesComboViewer.setContentProvider(new IStructuredContentProvider() {
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            }

            public void dispose() {
            }

            public Object[] getElements(Object inputElement) {
                if(!(inputElement instanceof IArchiRepository)) {
                    return new Object[0];
                }
                
                IArchiRepository repo = (IArchiRepository)inputElement;
                
                // Local Repo was deleted
                if(!repo.getLocalRepositoryFolder().exists()) {
                    return new Object[0];
                }
                
                try(Git git = Git.open(repo.getLocalRepositoryFolder())) {
                    //List<Ref> refs = git.branchList().call(); // Local branches
                    List<Ref> refs = git.branchList().setListMode(ListMode.ALL).call(); // All
                    return refs.toArray();
                }
                catch(IOException | GitAPIException ex) {
                    ex.printStackTrace();
                }

                return new Object[0];
            }
        });

        fBranchesComboViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                Ref ref = (Ref)element;
                return ref.getName();
            }
        });
        
        /*
         * Listen to Branch Selections
         */
        fBranchesComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged(SelectionChangedEvent event) {
                Ref ref = (Ref)event.getStructuredSelection().getFirstElement();
                getHistoryViewer().setRef(ref);
            }
        });
    }
    
    private void createHistorySection(Composite parent) {
        SashForm tableSash = new SashForm(parent, SWT.VERTICAL);
        tableSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        Composite tableComp = new Composite(tableSash, SWT.NONE);
        tableComp.setLayout(new UpdatingTableColumnLayout(tableComp));
        
        // This ensures a minumum and equal size and no horizontal size creep for the table
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 100;
        gd.heightHint = 50;
        tableComp.setLayoutData(gd);
        
        // History Table
        fHistoryTableViewer = new HistoryTableViewer(tableComp);
        
        // Comments Viewer
        fCommentViewer = new RevisionCommentViewer(tableSash);
        
        tableSash.setWeights(new int[] { 80, 20 });
        
        /*
         * Listen to History Selections to update local Actions
         */
        fHistoryTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged(SelectionChangedEvent event) {
                updateActions(event.getSelection());
            }
        });
        
        /*
         * Listen to Double-click Action
         */
        fHistoryTableViewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
            }
        });
    }
    
    /**
     * Make local actions
     */
    protected void makeActions() {
        fActionExtractCommit = new ExtractModelFromCommitAction(getViewSite().getWorkbenchWindow());
        fActionExtractCommit.setEnabled(false);
        
        fActionRestoreCommit = new RestoreCommitAction(getViewSite().getWorkbenchWindow());
        fActionRestoreCommit.setEnabled(false);
        
        fActionUndoLastCommit = new UndoLastCommitAction(getViewSite().getWorkbenchWindow());
        fActionUndoLastCommit.setEnabled(false);
        
        fActionResetToRemoteCommit = new ResetToRemoteCommitAction(getViewSite().getWorkbenchWindow());
        fActionResetToRemoteCommit.setEnabled(false);
        
        // Register the Keybinding for actions
//        IHandlerService service = (IHandlerService)getViewSite().getService(IHandlerService.class);
//        service.activateHandler(fActionRefresh.getActionDefinitionId(), new ActionHandler(fActionRefresh));
    }

    /**
     * Hook into a right-click menu
     */
    protected void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#HistoryPopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {
                fillContextMenu(manager);
            }
        });
        
        Menu menu = menuMgr.createContextMenu(getHistoryViewer().getControl());
        getHistoryViewer().getControl().setMenu(menu);
        
        getSite().registerContextMenu(menuMgr, getHistoryViewer());
    }
    
    /**
     * Make Any Local Bar Menu Actions
     */
//    protected void makeLocalMenuActions() {
//        IActionBars actionBars = getViewSite().getActionBars();
//
//        // Local menu items go here
//        IMenuManager manager = actionBars.getMenuManager();
//        manager.add(new Action("&View Management...") {
//            public void run() {
//                MessageDialog.openInformation(getViewSite().getShell(),
//                        "View Management",
//                        "This is a placeholder for the View Management Dialog");
//            }
//        });
//    }

    /**
     * Make Local Toolbar items
     */
    protected void makeLocalToolBarActions() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();

        manager.add(new Separator(IWorkbenchActionConstants.NEW_GROUP));
        
        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
        
        manager.add(new Separator());
    }
    
    /**
     * Update the Local Actions depending on the local selection 
     * @param selection
     */
    public void updateActions(ISelection selection) {
        RevCommit commit = (RevCommit)((IStructuredSelection)selection).getFirstElement();
        
        fActionExtractCommit.setCommit(commit);
        fActionRestoreCommit.setCommit(commit);
        
        fActionUndoLastCommit.update();
        fActionResetToRemoteCommit.update();
        
        fCommentViewer.setCommit(commit);
    }
    
    protected void fillContextMenu(IMenuManager manager) {
        // boolean isEmpty = getViewer().getSelection().isEmpty();

        manager.add(fActionExtractCommit);
        manager.add(fActionRestoreCommit);
        manager.add(new Separator());
        manager.add(fActionUndoLastCommit);
        manager.add(fActionResetToRemoteCommit);
    }

    HistoryTableViewer getHistoryViewer() {
        return fHistoryTableViewer;
    }
    
    @Override
    public void setFocus() {
        if(getHistoryViewer() != null) {
            getHistoryViewer().getControl().setFocus();
        }
    }
    
    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if(part == this || selection == null) {
            return;
        }
        
        Object selected = ((IStructuredSelection)selection).getFirstElement();
        
        IArchiRepository selectedRepository = null;
        
        // Repository selected
        if(selected instanceof IArchiRepository) {
            selectedRepository = (IArchiRepository)selected;
        }
        // Model selected, but is it in a git repo?
        else {
            IArchimateModel model = part.getAdapter(IArchimateModel.class);
            if(GraficoUtils.isModelInLocalRepository(model)) {
                selectedRepository = new ArchiRepository(GraficoUtils.getLocalRepositoryFolderForModel(model));
            }
        }
        
        // Update if selectedRepository is different 
        if(selectedRepository != null && !selectedRepository.equals(fSelectedRepository)) {
            // Set label text
            fRepoLabel.setText(Messages.HistoryView_0 + " " + selectedRepository.getName()); //$NON-NLS-1$
            
            // Set History first
            getHistoryViewer().doSetInput(selectedRepository);
            
            // Set Branches
            fBranchesComboViewer.setInput(selectedRepository);
            Object element = fBranchesComboViewer.getElementAt(0);
            if(element != null) {
                fBranchesComboViewer.setSelection(new StructuredSelection(element));
            }
            
            // Update actions
            fActionExtractCommit.setRepository(selectedRepository);
            fActionRestoreCommit.setRepository(selectedRepository);
            fActionUndoLastCommit.setRepository(selectedRepository);
            fActionResetToRemoteCommit.setRepository(selectedRepository);
            
            // Store last selected
            fSelectedRepository = selectedRepository;
        }
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repository) {
        if(repository.equals(fSelectedRepository)) {
            switch(eventName) {
                case IRepositoryListener.HISTORY_CHANGED:
                    fRepoLabel.setText(Messages.HistoryView_0 + " " + repository.getName()); //$NON-NLS-1$
                    getHistoryViewer().setInput(repository);
                    break;
                    
                case IRepositoryListener.REPOSITORY_DELETED:
                    fRepoLabel.setText(Messages.HistoryView_0);
                    getHistoryViewer().setInput(""); //$NON-NLS-1$
                    fSelectedRepository = null; // Reset this
                    break;
                    
                case IRepositoryListener.REPOSITORY_CHANGED:
                    fRepoLabel.setText(Messages.HistoryView_0 + " " + repository.getName()); //$NON-NLS-1$
                    break;

                default:
                    break;
            }
        }
    }
    
    @Override
    public void dispose() {
        super.dispose();
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
        RepositoryListenerManager.INSTANCE.removeListener(this);
    }
    

    // =================================================================================
    //                       Contextual Help support
    // =================================================================================
    
    public int getContextChangeMask() {
        return NONE;
    }

    public IContext getContext(Object target) {
        return HelpSystem.getContext(HELP_ID);
    }

    public String getSearchExpression(Object target) {
        return Messages.HistoryView_1;
    }
}
