/**
 * @file mock index module
 * @author blueking
 */

import moment from 'moment'
import faker from 'faker'
import chalk from 'chalk'

import {randomInt, sleep} from '../util'

export async function response(getArgs, postArgs, req) {
    console.log(chalk.cyan('req', req.method))
    console.log(chalk.cyan('getArgs', JSON.stringify(getArgs, null, 0)))
    console.log(chalk.cyan('postArgs', JSON.stringify(postArgs, null, 0)))
    console.log()
    const invoke = getArgs.invoke
    if (invoke === 'enterExample1') {
        const delay = getArgs.delay
        await sleep(delay)
        return {
            // http status code, 后端返回的数据没有这个字段，这里模拟这个字段是为了在 mock 时更灵活的自定义 http status code，
            // 同时热更新即改变 http status code 后无需重启服务，这个字段的处理参见 build/ajax-middleware.js
            // statusCode: 401,
            code: 0,
            data: {
                msg: `我是 enterExample1 请求返回的数据。本请求需耗时 ${delay} ms`
            },
            message: 'ok'
        }
    }
    else if (invoke === 'enterExample2') {
        const delay = postArgs.delay
        await sleep(delay)
        return {
            // http status code, 后端返回的数据没有这个字段，这里模拟这个字段是为了在 mock 时更灵活的自定义 http status code，
            // 同时热更新即改变 http status code 后无需重启服务，这个字段的处理参见 build/ajax-middleware.js
            // statusCode: 401,
            code: 0,
            data: {
                msg: `我是 enterExample2 请求返回的数据。本请求需耗时 ${delay} ms`
            },
            message: 'ok'
        }
    }
    else if (invoke === 'btn1') {
        await sleep(3000)
        return {
            // http status code, 后端返回的数据没有这个字段，这里模拟这个字段是为了在 mock 时更灵活的自定义 http status code，
            // 同时热更新即改变 http status code 后无需重启服务，这个字段的处理参见 build/ajax-middleware.js
            // statusCode: 401,
            code: 0,
            data: {
                msg: `我是 btn1 请求返回的数据。本请求需耗时 3000 ms. ${+new Date}`
            },
            message: 'ok'
        }
    }
    else if (invoke === 'btn2') {
        await sleep(3000)
        return {
            // http status code, 后端返回的数据没有这个字段，这里模拟这个字段是为了在 mock 时更灵活的自定义 http status code，
            // 同时热更新即改变 http status code 后无需重启服务，这个字段的处理参见 build/ajax-middleware.js
            // statusCode: 401,
            code: 0,
            data: {
                msg: `我是 btn2 请求返回的数据。本请求需耗时 3000 ms. ${+new Date}`
            },
            message: 'ok'
        }
    }
    else if (invoke === 'del') {
        return {
            code: 0,
            data: {
                msg: `我是 del 请求返回的数据。请求参数为 ${postArgs.time}`
            },
            message: 'ok'
        }
    }
    else if (invoke === 'get') {
        // await sleep(1000)
        return {
            // http status code, 后端返回的数据没有这个字段，这里模拟这个字段是为了在 mock 时更灵活的自定义 http status code，
            // 同时热更新即改变 http status code 后无需重启服务，这个字段的处理参见 build/ajax-middleware.js
            // statusCode: 401,
            code: 0,
            data: {
                reqTime: getArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'post') {
        // await sleep(1000)
        return {
            code: 0,
            data: {
                reqTime: postArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'long') {
        await sleep(5000)
        return {
            code: 0,
            data: {
                reqTime: getArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'long1') {
        await sleep(2000)
        return {
            code: 0,
            data: {
                reqTime: getArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'same') {
        await sleep(5000)
        return {
            code: 0,
            data: {
                reqTime: getArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'postSame') {
        await sleep(5000)
        return {
            code: 0,
            // statusCode: 401,
            data: {
                reqTime: postArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'go') {
        await sleep(2000)
        return {
            code: 0,
            statusCode: 400,
            data: {
                reqTime: postArgs.time,
                resTime: +new Date
            },
            message: 'ok'
        }
    }
    else if (invoke === 'user') {
        return {
            code: 0,
            data: {
                name: 'name'
            },
            message: 'ok'
        }
    } else if (invoke === 'metadata') {
        const data = require('./tool-meta.json')
        return {
            code: 0,
            data,
            message: 'ok'
        }
    }
    else if (invoke === 'tableData') {
        await sleep(1000)
        return {
            code: 0,
            data: [
                {
                    ip: '192.168.0.1',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.2',
                    source: 'CMDB',
                    status: '正常',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.3',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.1',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.2',
                    source: 'CMDB',
                    status: '正常',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.3',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.1',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.2',
                    source: 'CMDB',
                    status: '正常',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.3',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.1',
                    source: '蓝盾',
                    status: '创建中',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                },
                {
                    ip: '192.168.0.2',
                    source: 'CMDB',
                    status: '正常',
                    create_time: '2018-05-25 15:02:24',
                    children: [
                        {
                            name: '游戏体验管理',
                            count: '23',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '游戏体验管理'
                        },
                        {
                            name: 'guild(游戏公会)',
                            count: '2',
                            creator: 'blueking',
                            create_time: '2017-10-10 11:12',
                            desc: '无数据测试'
                        }
                    ]
                }
            ],
            message: 'ok'
        }
    }
    return {
        code: 0,
        data: {}
    }
}

