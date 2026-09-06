/**
 * 文件作用：
 * 弹窗栈（模块级单例），为全应用弹窗提供「栈式互斥」语义：
 * - 同一时刻至多一个弹窗可见；
 * - 打开新弹窗时，当前栈顶弹窗自动「隐藏」（仅切可见性，内部状态保留）；
 * - 关闭当前弹窗时，自动「恢复」上一个弹窗（重新显示，不重置内部状态）；
 * - 没有上一个弹窗时正常关闭（什么都不做）。
 * <p>
 * 使用约定：
 * - 每个参与互斥的弹窗组件持有一个本组件唯一的 Symbol 身份（key）；
 * - 组件对外提供 hide()（仅隐藏）/ show()（仅恢复显示）两个原语，
 *   与 open()（重置并显示）/ 用户关闭（真正关闭）区分开；
 * - 弹窗「打开」时调用 open(entry) 入栈并顶掉栈顶；
 *   弹窗「用户关闭」（取消/完成/X/Esc/遮罩）时调用 close(key) 出栈并恢复上一个；
 *   组件销毁（卸载）时调用 remove(key) 只出栈不恢复，避免误恢复正在卸载的弹窗。
 * <p>
 * 关键点：
 * - 弹窗被栈顶掉（hide）时，ElDialog 的 close 事件也会触发，
 *   组件需用内部守卫标志区分「栈隐藏」与「用户关闭」，避免误出栈；
 * - <b>参与栈互斥且内含其他弹窗组件的弹窗，不能使用 destroy-on-close</b>：
 *   hide 时内容销毁会把内嵌子弹窗组件一并卸载（其 onBeforeUnmount 会移除栈条目），
 *   导致子弹窗打开后瞬间消失。状态重置应由组件的 open() 显式完成，而非依赖内容销毁；
 * - 从页面直接打开的并列弹窗（无上一个弹窗）可不接入本栈，行为无差异。
 */
export interface DialogStackEntry {
  /** 组件实例身份，避免同名组件多实例冲突 */
  key: symbol
  /** 仅隐藏自身（visible=false），不重置内部状态 */
  hide: () => void
  /** 仅恢复显示自身（visible=true），不重置内部状态 */
  show: () => void
}

/** 模块级栈状态，保证全应用单例（各组件调用 useDialogStack 共享同一栈） */
const stack: DialogStackEntry[] = []

export const useDialogStack = () => {
  /**
   * 方法效果：
   * 打开弹窗：先隐藏当前栈顶（如有），再把当前弹窗入栈。
   * 参数：
   * - `entry`：当前弹窗的栈条目（身份 key + hide/show 原语）。
   * 返回值：
   * - 无返回值；副作用是更新栈状态并隐藏栈顶弹窗。
   */
  const open = (entry: DialogStackEntry) => {
    const top = stack[stack.length - 1]
    if (top && top.key !== entry.key) {
      top.hide()
    }

    // 若自身已在栈中（重复 open），先移除再入栈，保持栈顺序正确
    const existingIndex = stack.findIndex((item) => item.key === entry.key)
    if (existingIndex !== -1) {
      stack.splice(existingIndex, 1)
    }
    stack.push(entry)
  }

  /**
   * 方法效果：
   * 关闭弹窗（用户关闭路径）：从栈中移除当前弹窗，若栈中还有上一个弹窗则自动恢复它。
   * 参数：
   * - `entryKey`：当前弹窗的栈身份 key。
   * 返回值：
   * - 无返回值；副作用是更新栈状态并恢复上一个弹窗。
   */
  const close = (entryKey: symbol) => {
    const index = stack.findIndex((item) => item.key === entryKey)
    if (index === -1) {
      return
    }
    stack.splice(index, 1)
    const nextTop = stack[stack.length - 1]
    if (nextTop) {
      nextTop.show()
    }
  }

  /**
   * 方法效果：
   * 注销弹窗（组件销毁路径）：只从栈中移除自身，不恢复上一个，
   * 避免页面销毁/组件卸载时误恢复同样正在卸载的弹窗。
   * 参数：
   * - `entryKey`：当前弹窗的栈身份 key。
   * 返回值：
   * - 无返回值；副作用是更新栈状态。
   */
  const remove = (entryKey: symbol) => {
    const index = stack.findIndex((item) => item.key === entryKey)
    if (index !== -1) {
      stack.splice(index, 1)
    }
  }

  return { open, close, remove }
}
