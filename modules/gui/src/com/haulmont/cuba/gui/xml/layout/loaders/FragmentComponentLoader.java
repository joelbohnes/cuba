/*
 * Copyright (c) 2008-2016 Haulmont.
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
 *
 */
package com.haulmont.cuba.gui.xml.layout.loaders;

import com.haulmont.cuba.gui.GuiDevelopmentException;
import com.haulmont.cuba.gui.components.Fragment;
import com.haulmont.cuba.gui.components.sys.FragmentImplementation;
import com.haulmont.cuba.gui.components.sys.FrameImplementation;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.logging.ScreenLifeCycle;
import com.haulmont.cuba.gui.model.impl.ScreenDataImpl;
import com.haulmont.cuba.gui.screen.FrameOwner;
import com.haulmont.cuba.gui.screen.ScreenFragment;
import com.haulmont.cuba.gui.screen.ScreenOptions;
import com.haulmont.cuba.gui.sys.FragmentContextImpl;
import com.haulmont.cuba.gui.sys.FragmentHelper;
import com.haulmont.cuba.gui.sys.FragmentHelper.FragmentLoaderInitTask;
import com.haulmont.cuba.gui.sys.ScreenContextImpl;
import com.haulmont.cuba.gui.sys.UiControllerProperty;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import com.haulmont.cuba.gui.xml.layout.LayoutLoader;
import com.haulmont.cuba.gui.xml.layout.ScreenXmlLoader;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.perf4j.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.haulmont.cuba.gui.logging.UIPerformanceLogger.createStopWatch;
import static com.haulmont.cuba.gui.screen.UiControllerUtils.*;
import static com.haulmont.cuba.gui.sys.FragmentHelper.FragmentLoaderInjectTask;
import static com.haulmont.cuba.gui.sys.FragmentHelper.NAME;

public class FragmentComponentLoader extends ContainerLoader<Fragment> {

    protected ComponentLoader fragmentLoader;
    protected ComponentLoaderContext innerContext;

    @Override
    public void createComponent() {
        String src = element.attributeValue("src");
        String screenId = element.attributeValue("screen");
        if (src == null
                && screenId == null) {
            throw new GuiDevelopmentException("Either 'src' or 'screen' must be specified for 'frame'",
                    context, "fragment", element.attributeValue("id"));
        }

        String fragmentId;
        if (element.attributeValue("id") != null) {
            fragmentId = element.attributeValue("id");
        } else if (screenId != null){
            fragmentId = screenId;
        } else {
            fragmentId = src;
        }

        FragmentHelper fragmentHelper = getFragmentHelper();

        WindowInfo windowInfo;
        if (src == null) {
            // load screen class only once
            windowInfo = getWindowConfig().getWindowInfo(screenId).resolve();
        } else {
            windowInfo = fragmentHelper.createFakeWindowInfo(src, fragmentId);
        }

        StopWatch createStopWatch = createStopWatch(ScreenLifeCycle.CREATE, windowInfo.getId());

        Fragment fragment = factory.create(Fragment.NAME);
        ScreenFragment controller = fragmentHelper.createController(windowInfo, fragment);

        // setup screen and controller
        ComponentLoaderContext parentContext = (ComponentLoaderContext) getContext();

        FrameOwner hostController = parentContext.getFrame().getFrameOwner();

        setHostController(controller, hostController);
        setWindowId(controller, windowInfo.getId());
        setFrame(controller, fragment);
        setScreenContext(controller,
                new ScreenContextImpl(windowInfo, parentContext.getOptions(), getScreenContext(hostController))
        );
        setScreenData(controller, new ScreenDataImpl());

        FragmentImplementation fragmentImpl = (FragmentImplementation) fragment;
        fragmentImpl.setFrameOwner(controller);
        fragmentImpl.setId(fragmentId);

        FragmentContextImpl frameContext = new FragmentContextImpl(fragment, innerContext);
        ((FrameImplementation) fragment).setContext(frameContext);

        // load from XML if needed

        if (windowInfo.getTemplate() != null) {
            String frameId = fragmentId;
            if (parentContext.getFullFrameId() != null) {
                frameId = parentContext.getFullFrameId() + "." + frameId;
            }

            innerContext = new ComponentLoaderContext(getComponentContext().getOptions());
            innerContext.setMessagesPack(fragmentHelper.getMessagePack(windowInfo.getTemplate()));
            innerContext.setCurrentFrameId(fragmentId);
            innerContext.setFullFrameId(frameId);
            innerContext.setFrame(fragment);
            innerContext.setParent(parentContext);
            innerContext.setProperties(loadProperties(element));

            LayoutLoader layoutLoader = getLayoutLoader(innerContext);

            ScreenXmlLoader screenXmlLoader = beanLocator.get(ScreenXmlLoader.NAME);

            Element rootElement = screenXmlLoader.load(windowInfo.getTemplate(), windowInfo.getId(),
                    getComponentContext().getParams());

            String messagesPack = rootElement.attributeValue("messagesPack");
            if (messagesPack != null) {
                innerContext.setMessagesPack(messagesPack);
            }

            this.fragmentLoader = layoutLoader.createFragmentContent(fragment, rootElement);
        }

        createStopWatch.stop();

        this.resultComponent = fragment;
    }

    protected FragmentHelper getFragmentHelper() {
        return beanLocator.get(NAME);
    }

    @Override
    public void loadComponent() {
        loadAliases();

        if (getComponentContext().getFrame() != null) {
            resultComponent.setFrame(getComponentContext().getFrame());
        }

        String src = element.attributeValue("src");
        String screenId = element.attributeValue("screen");
        String screenPath = StringUtils.isEmpty(screenId) ? src : screenId;
        if (element.attributeValue("id") != null) {
            screenPath = element.attributeValue("id");
        }
        if (getComponentContext().getFrame() != null) {
            String parentId = getComponentContext().getFullFrameId();
            if (StringUtils.isNotEmpty(parentId)) {
                screenPath = parentId + "." + screenPath;
            }
        }

        StopWatch loadStopWatch = createStopWatch(ScreenLifeCycle.LOAD, screenPath);

        // if fragment has XML descriptor

        if (fragmentLoader != null) {
            fragmentLoader.loadComponent();
        }

        // load properties after inner context, they must override values defined inside of fragment

        assignXmlDescriptor(resultComponent, element);
        loadVisible(resultComponent, element);
        loadEnable(resultComponent, element);

        loadStyleName(resultComponent, element);
        loadResponsive(resultComponent, element);
        loadCss(resultComponent, element);

        loadAlign(resultComponent, element);

        loadHeight(resultComponent, element);
        loadWidth(resultComponent, element);

        loadIcon(resultComponent, element);
        loadCaption(resultComponent, element);
        loadDescription(resultComponent, element);

        loadStopWatch.stop();

        // propagate init phases

        ComponentLoaderContext parentContext = (ComponentLoaderContext) getContext();

        if (innerContext != null) {
            parentContext.getInjectTasks().addAll(innerContext.getInjectTasks());
            parentContext.getInitTasks().addAll(innerContext.getInitTasks());
            parentContext.getPostInitTasks().addAll(innerContext.getPostInitTasks());
        }

        ScreenOptions options = parentContext.getOptions();
        parentContext.addInjectTask(new FragmentLoaderInjectTask(resultComponent, options, beanLocator));
        parentContext.addInitTask(new FragmentLoaderInitTask(resultComponent, options, innerContext, beanLocator));
    }

    protected List<UiControllerProperty> loadProperties(Element element) {
        Element propsEl = element.element("properties");
        if (propsEl == null) {
            return Collections.emptyList();
        }

        List<Element> propElements = propsEl.elements("property");
        if (propElements.isEmpty()) {
            return Collections.emptyList();
        }

        List<UiControllerProperty> properties = new ArrayList<>(propElements.size());

        for (Element property : propElements) {
            String name = property.attributeValue("name");
            if (name == null || name.isEmpty()) {
                throw new GuiDevelopmentException("Screen fragment property cannot have empty name", context);
            }

            String value = property.attributeValue("value");
            String ref = property.attributeValue("ref");

            if (StringUtils.isNotEmpty(value) && StringUtils.isNotEmpty(ref)) {
                throw new GuiDevelopmentException("Screen fragment property can have either a value or a reference. Property: " +
                        name, context);
            }

            if (StringUtils.isNotEmpty(value)) {
                properties.add(new UiControllerProperty(name, value, UiControllerProperty.Type.VALUE));
            } else if (StringUtils.isNotEmpty(ref)) {
                properties.add(new UiControllerProperty(name, ref, UiControllerProperty.Type.REFERENCE));
            } else {
                throw new GuiDevelopmentException("No value or reference found for screen fragment property: " + name,
                        context);
            }
        }

        return properties;
    }

    protected void loadAliases() {
        if (fragmentLoader instanceof FragmentLoader) {
            ComponentLoaderContext frameLoaderInnerContext = (ComponentLoaderContext) fragmentLoader.getContext();
            for (Element aliasElement : element.elements("dsAlias")) {
                String aliasDatasourceId = aliasElement.attributeValue("alias");
                String originalDatasourceId = aliasElement.attributeValue("datasource");
                if (StringUtils.isNotBlank(aliasDatasourceId) && StringUtils.isNotBlank(originalDatasourceId)) {
                    frameLoaderInnerContext.getAliasesMap().put(aliasDatasourceId, originalDatasourceId);
                }
            }
        }
    }

    protected WindowConfig getWindowConfig() {
        return beanLocator.get(WindowConfig.NAME);
    }
}