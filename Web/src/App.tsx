import VConsole from "vconsole"
import { isMobile } from "@/utils"
import { Tabs } from 'antd';
import type { TabsProps } from 'antd';

import Living from "@/components/living"
import Pure1v1 from "@/components/pure-1v1"
import Version from "@/components/version"

if (isMobile()) {
  const vConsole = new VConsole()
}


const items: TabsProps['items'] = [
  {
    key: 'pure1v1',
    label: '纯1v1',
    children: <Pure1v1></Pure1v1>,
  },
  {
    key: 'living',
    label: '秀场转1v1',
    children: <Living></Living>,
  },
];

function App() {

  return <div className="app">
    <Tabs centered items={items} destroyInactiveTabPane={true} />
    <Version></Version>
  </div>
}

export default App
