/**
 * Copyright © 2018 Sven Ruppert (sven.ruppert@gmail.com)
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
package org.rapidpm.vaadin.api.i18n;

import java.util.Locale;

import com.vaadin.flow.i18n.I18NProvider;

public class DefaultTitleFormatter implements TitleFormatter {

  @Override
  public String applyWithException(I18NProvider i18NProvider , Locale locale , String key) throws Exception {
    return i18NProvider.getTranslation(key, locale);
  }
}
