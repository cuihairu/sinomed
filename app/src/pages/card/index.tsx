import { View, Text } from '@tarojs/components'
import './index.css'

/**
 * 卡项 tab：顾客购卡/余次自助查询属规划功能（既定裁定，界面如实占位不造假数据），
 * 本页给出现实的查询途径：到店前台 / 致电门店。
 */
export default function Card() {
  return (
    <View className="card-page">
      <View className="sec">
        <Text className="sec-h3">我的卡项</Text>
        <Text className="plan-pill">规划功能</Text>
      </View>
      <View className="empty-card">
        <Text className="ec-zi">卡</Text>
        <Text className="ec-t">
          购卡与余次查询为规划功能，上线后将在这里展示您的卡项与剩余次数
        </Text>
      </View>

      <View className="sec">
        <Text className="sec-h3">现在怎么查</Text>
      </View>
      <View className="how-card">
        <View className="how-row">
          <Text className="how-k">到店</Text>
          <Text className="how-v">前台报手机号，店员为您查询余次并安排核销</Text>
        </View>
        <View className="how-row">
          <Text className="how-k">致电</Text>
          <Text className="how-v">联系门店电话，即可查询购卡与剩余次数</Text>
        </View>
      </View>
      <View className="plan-note">卡项由门店开具、到店核销；预约到店可走「预约」页。</View>
    </View>
  )
}
