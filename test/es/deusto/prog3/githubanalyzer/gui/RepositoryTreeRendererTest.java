package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;

class RepositoryTreeRendererTest {
    private RepoStats repo() {
        RepoStats repo = new RepoStats();
        repo.setUrl("https://github.com/course/repo");
        repo.setName("repo"); repo.setAnalysisComplete(true); repo.setCommits(2);
        return repo;
    }

    @Test void publicAndPrivateIconsNeverBecomeTheRootIconWhenAnalysisIsMissingOrEmpty() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var renderer = new RepositoryTreeRenderer(new HashMap<>(), new HashSet<>());
            var tree = new JTree();
            RepoStats repo = repo();
            var node = new DefaultMutableTreeNode(repo);
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            var privateIcon = renderer.getIcon(); assertNotNull(privateIcon);
            repo.setAnalysisComplete(false);
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertSame(privateIcon, renderer.getIcon());
            assertEquals(RepositoryTreeRenderer.MISSING, renderer.getForeground());
            repo.setPublic(true);
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            var publicIcon = renderer.getIcon(); assertNotNull(publicIcon); assertNotSame(privateIcon, publicIcon);
            repo.setAnalysisComplete(true); repo.setCommits(0);
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertSame(publicIcon, renderer.getIcon());
            assertEquals(RepositoryTreeRenderer.MISSING, renderer.getForeground());
            renderer.getTreeCellRendererComponent(tree, new DefaultMutableTreeNode("Repositories"), false, true, false, 0, false);
            assertNotSame(publicIcon, renderer.getIcon()); assertNotSame(privateIcon, renderer.getIcon());
        });
    }

    @Test void pendingUpdatesAreOrangeAndZeroCountsDoNotLeaveASuffixOrAStaleColor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var updates = new HashMap<String, UpdateStatus>();
            var failed = new HashSet<String>();
            var renderer = new RepositoryTreeRenderer(updates, failed);
            RepoStats repo = repo(); var tree = new JTree(); var node = new DefaultMutableTreeNode(repo);
            updates.put(repo.getUrl(), new UpdateStatus(7, true, null));
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertEquals("repo (7)", renderer.getText());
            assertEquals(RepositoryTreeRenderer.PENDING, renderer.getForeground());
            updates.put(repo.getUrl(), new UpdateStatus(0, true, null));
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertEquals("repo (changes pending)", renderer.getText());
            assertEquals(RepositoryTreeRenderer.PENDING, renderer.getForeground());
            updates.put(repo.getUrl(), new UpdateStatus(0, false, null));
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertEquals("repo", renderer.getText());
            assertNotEquals(RepositoryTreeRenderer.PENDING, renderer.getForeground());
            failed.add(repo.getUrl());
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertTrue(renderer.getText().contains("refresh failed"));
            assertEquals(RepositoryTreeRenderer.PENDING, renderer.getForeground());
        });
    }

    @Test void metadataUpdatesCanResolveVisibilityWithoutAnyDownloadedAnalysisAndErrorsStayLiteral() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var updates = new HashMap<String, UpdateStatus>();
            var renderer = new RepositoryTreeRenderer(updates, new HashSet<>());
            RepoStats repo = repo(); repo.setAnalysisComplete(false);
            var tree = new JTree(); var node = new DefaultMutableTreeNode(repo);
            updates.put(repo.getUrl(), new UpdateStatus(3, true, null, true));
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            var publicIcon = renderer.getIcon();
            repo.setPublic(true); updates.clear();
            renderer.getTreeCellRendererComponent(tree, node, false, false, true, 1, false);
            assertSame(publicIcon, renderer.getIcon());
            repo.setName("<html><img src='http://127.0.0.1/untrusted'>");
            updates.put(repo.getUrl(), new UpdateStatus(-1, false, "<html>remote error"));
            renderer.getTreeCellRendererComponent(tree, node, true, false, true, 1, true);
            assertNull(renderer.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            assertTrue(renderer.getToolTipText().startsWith("Repository:"));
            assertSame(publicIcon, renderer.getIcon());
        });
    }
}
