import { VERSION } from "@/callApi"
import { Divider } from "antd"
import "./index.css"

const Version = () => {

  return <Divider className="version" plain>CallApi Version: {VERSION}</Divider>
}


export default Version
